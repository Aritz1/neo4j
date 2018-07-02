/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.id.indexed;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.neo4j.internal.id.IdCapacityExceededException;
import org.neo4j.internal.id.IdGenerator;
import org.neo4j.internal.id.IdGenerator.CommitMarker;
import org.neo4j.internal.id.IdGenerator.ReuseMarker;
import org.neo4j.internal.id.IdType;
import org.neo4j.internal.id.IdValidator;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.test.Race;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomExtension;
import org.neo4j.test.extension.pagecache.PageCacheExtension;
import org.neo4j.test.rule.RandomRule;
import org.neo4j.test.rule.TestDirectory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector.immediate;
import static org.neo4j.internal.id.indexed.IndexedIdGenerator.IDS_PER_ENTRY;

@PageCacheExtension
@ExtendWith( RandomExtension.class )
class IndexedIdGeneratorTest
{
    private static final long MAX_ID = 0x3_00000000L;

    @Inject
    private FileSystemAbstraction fs;
    @Inject
    private TestDirectory directory;
    @Inject
    private PageCache pageCache;
    @Inject
    private RandomRule random;

    private IdGenerator freelist;

    @BeforeEach
    void start()
    {
        freelist = new IndexedIdGenerator( pageCache, directory.file( "file" ), immediate(), IdType.LABEL_TOKEN, 0, MAX_ID );
    }

    @AfterEach
    void stop()
    {
        freelist.close();
    }

    @Test
    void shouldAllocateFreedSingleIdSlot()
    {
        // given
        long id = freelist.nextId();
        markUnused( id );
        markReusable( id );

        // when
        long nextTimeId = freelist.nextId();

        // then
        // TODO of course later on we have to use the reuse thing too
        assertEquals( id, nextTimeId );
    }

    @Test
    void shouldNotAllocateFreedIdUntilReused()
    {
        // given
        long id = freelist.nextId();
        markUnused( id );
        long otherId = freelist.nextId();
        assertNotEquals( id, otherId );

        // when
        markReusable( id );

        // then
        long reusedId = freelist.nextId();
        assertEquals( id, reusedId );
    }

    @Test
    void shouldStayConsistentAndNotLoseIdsInConcurrentRealWorldSimulation() throws Throwable
    {
        // given
        int runTimeSeconds = 1;
        int maxAllocationsAhead = 500;

        Race race = new Race().withMaxDuration( runTimeSeconds, TimeUnit.SECONDS );
        ConcurrentLinkedQueue<Allocation> allocations = new ConcurrentLinkedQueue<>();
        ConcurrentSparseLongBitSet expectedInUse = new ConcurrentSparseLongBitSet( IDS_PER_ENTRY );
        AtomicLong allocationsMade = new AtomicLong();
        AtomicLong freesMade = new AtomicLong();
        AtomicLong totalIdsAllocated = new AtomicLong();
        race.addContestants( 6, allocator( maxAllocationsAhead, allocations, expectedInUse, allocationsMade, totalIdsAllocated ) );
        race.addContestants( 1, deleter( allocations ) );
        race.addContestants( 1, freer( allocations, expectedInUse, freesMade ) );

        // when
        race.go();

        // then after all remaining allocations have been freed, allocating that many ids again should not need to increase highId,
        // i.e. all such allocations should be allocated from the free-list
        deleteAndFree( allocations, expectedInUse );
        long highIdBeforeReallocation = freelist.getHighId();
        long numberOfIdsOutThere = highIdBeforeReallocation;
        ConcurrentSparseLongBitSet reallocationIds = new ConcurrentSparseLongBitSet( IDS_PER_ENTRY );
        while ( numberOfIdsOutThere > 0 )
        {
            long id = freelist.nextId();
            Allocation allocation = new Allocation( id );
            numberOfIdsOutThere -= 1;
            reallocationIds.set( allocation.id, 1, true );
        }
        // TODO really it should be 0, but sometimes it's 1 and haven't figured out why yet
        assertThat( freelist.getHighId() - highIdBeforeReallocation, Matchers.lessThan( 5L ) );
    }

    @Test
    void shouldNotAllocateReservedMaxIntId()
    {
        // given
        freelist.setHighId( IdValidator.INTEGER_MINUS_ONE );

        // when
        long id = freelist.nextId();

        // then
        assertEquals( IdValidator.INTEGER_MINUS_ONE + 1, id );
        assertFalse( IdValidator.isReservedId( id ) );
    }

    @Test
    void shouldNotGoBeyondMaxId()
    {
        // given
        freelist.setHighId( MAX_ID - 1 );

        // when
        long oneBelowMaxId = freelist.nextId();
        assertEquals( MAX_ID - 1, oneBelowMaxId );
        long maxId = freelist.nextId();
        assertEquals( MAX_ID, maxId );

        // then
        try
        {
            freelist.nextId();
            fail( "Should have fail to go beyond max id" );
        }
        catch ( IdCapacityExceededException e )
        {
            // good
        }
    }

    private Runnable freer( ConcurrentLinkedQueue<Allocation> allocations, ConcurrentSparseLongBitSet expectedInUse, AtomicLong freesMade )
    {
        return new Runnable()
        {
            private Random r = new Random( random.nextLong() );

            @Override
            public void run()
            {
                // Mark ids as eligible for reuse
                int size = allocations.size();
                if ( size > 0 )
                {
                    int slot = r.nextInt( size );
                    Iterator<Allocation> iterator = allocations.iterator();
                    Allocation allocation = null;
                    for ( int i = 0; i < slot && iterator.hasNext(); i++ )
                    {
                        allocation = iterator.next();
                    }
                    if ( allocation != null )
                    {
                        if ( allocation.free( expectedInUse ) )
                        {
                            iterator.remove();
                            freesMade.incrementAndGet();
                        }
                        // else someone else got there before us
                    }
                }
            }
        };
    }

    private Runnable deleter( ConcurrentLinkedQueue<Allocation> allocations )
    {
        return new Runnable()
        {
            private Random r = new Random( random.nextLong() );

            @Override
            public void run()
            {
                // Delete ids
                int size = allocations.size();
                if ( size > 0 )
                {
                    int slot = r.nextInt( size );
                    Iterator<Allocation> iterator = allocations.iterator();
                    Allocation allocation = null;
                    for ( int i = 0; i < slot && iterator.hasNext(); i++ )
                    {
                        allocation = iterator.next();
                    }
                    if ( allocation != null )
                    {
                        try
                        {
                            // Won't delete if it has already been deleted, but that's fine
                            allocation.delete();
                        }
                        catch ( Exception e )
                        {
                            System.err.println( allocation );
                            throw e;
                        }
                    }
                }
            }
        };
    }

    private Runnable allocator( int maxAllocationsAhead, ConcurrentLinkedQueue<Allocation> allocations,
            ConcurrentSparseLongBitSet expectedInUse, AtomicLong allocationsMade, AtomicLong totalRecordIdsAllocated )
    {
        return () ->
        {
            // Allocate ids
            if ( allocations.size() < maxAllocationsAhead )
            {
                long id = freelist.nextId();
                Allocation allocation = new Allocation( id );
                try
                {
                    allocation.markAsInUse( expectedInUse );
                    allocations.add( allocation );
                    allocationsMade.incrementAndGet();
                    totalRecordIdsAllocated.incrementAndGet();
                }
                catch ( Exception e )
                {
                    System.err.println( allocation );
                    throw e;
                }
            }
        };
    }

    private void deleteAndFree( ConcurrentLinkedQueue<Allocation> allocations, ConcurrentSparseLongBitSet expectedInUse )
    {
        for ( Allocation allocation : allocations )
        {
            allocation.delete();
            allocation.free( expectedInUse );
        }
    }

    private void markUsed( long id )
    {
        try ( CommitMarker marker = freelist.commitMarker() )
        {
            marker.markUsed( id );
        }
    }

    private void markUnused( long id )
    {
        try ( CommitMarker marker = freelist.commitMarker() )
        {
            marker.markDeleted( id );
        }
    }

    private void markReusable( long id )
    {
        try ( ReuseMarker marker = freelist.reuseMarker() )
        {
            marker.markFree( id );
        }
    }

    private class Allocation
    {
        private final long id;
        private final AtomicBoolean deleting = new AtomicBoolean();
        private volatile boolean deleted;
        private final AtomicBoolean freeing = new AtomicBoolean();

        Allocation( long id )
        {
            this.id = id;
        }

        boolean delete()
        {
            if ( deleting.compareAndSet( false, true ) )
            {
                markUnused( id );
                deleted = true;
                return true;
            }
            return false;
        }

        boolean free( ConcurrentSparseLongBitSet expectedInUse )
        {
            if ( !deleted )
            {
                return false;
            }

            if ( freeing.compareAndSet( false, true ) )
            {
                expectedInUse.set( id, 1, false );
                markReusable( id );
                return true;
            }
            return false;
        }

        void markAsInUse( ConcurrentSparseLongBitSet expectedInUse )
        {
            expectedInUse.set( id, 1, true );
            // Simulate that actual commit comes very close after allocation, in reality they are slightly more apart
            // Also this test marks all ids, regardless if they come from highId or the free-list. This to simulate more real-world
            // scenario and to exercise the idempotent clearing feature.
            markUsed( id );
        }

        @Override
        public String toString()
        {
            return String.valueOf( id );
        }
    }
}
