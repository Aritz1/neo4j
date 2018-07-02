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
package org.neo4j.kernel.impl.storemigration.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.IntStream;

import org.neo4j.common.EntityType;
import org.neo4j.configuration.Config;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.id.DefaultIdGeneratorFactory;
import org.neo4j.internal.id.IdType;
import org.neo4j.internal.schema.IndexConfig;
import org.neo4j.internal.schema.SchemaRule;
import org.neo4j.internal.schema.constraints.ConstraintDescriptorFactory;
import org.neo4j.io.fs.EphemeralFileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.kernel.impl.store.format.standard.StandardV3_4;
import org.neo4j.kernel.impl.store.record.DynamicRecord;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.storageengine.api.ConstraintRule;
import org.neo4j.storageengine.api.DefaultStorageIndexReference;
import org.neo4j.storageengine.api.StorageIndexReference;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.pagecache.EphemeralPageCacheExtension;
import org.neo4j.test.rule.TestDirectory;

import static java.nio.ByteBuffer.wrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector.immediate;
import static org.neo4j.internal.helpers.collection.Iterables.asCollection;
import static org.neo4j.internal.schema.SchemaDescriptor.forLabel;
import static org.neo4j.internal.schema.SchemaDescriptor.fulltext;

@EphemeralPageCacheExtension
class SchemaStore35Test
{
    private static final String PROVIDER_KEY = "quantum-dex";
    private static final String PROVIDER_VERSION = "25.0";

    @Inject
    private PageCache pageCache;
    @Inject
    private EphemeralFileSystemAbstraction fs;
    @Inject
    private TestDirectory testDirectory;

    private SchemaStore35 store;

    @BeforeEach
    void before()
    {
        Config config = Config.defaults();
        DefaultIdGeneratorFactory idGeneratorFactory = new DefaultIdGeneratorFactory( fs, pageCache, immediate() );
        NullLogProvider logProvider = NullLogProvider.getInstance();
        store = new SchemaStore35( testDirectory.file( "schema35" ), testDirectory.file( "schema35.db.id" ), config, IdType.SCHEMA,
                idGeneratorFactory, pageCache, logProvider, StandardV3_4.RECORD_FORMATS );
        store.initialise( true );
    }

    @AfterEach
    void after()
    {
        store.close();
    }

    @Test
    void storeAndLoadSchemaRule() throws Exception
    {
        // GIVEN
        StorageIndexReference indexRule =
                new DefaultStorageIndexReference( forLabel( 1, 4 ), PROVIDER_KEY, PROVIDER_VERSION, store.nextId(), Optional.empty(), false, null );

        // WHEN
        StorageIndexReference readIndexRule = (StorageIndexReference) SchemaRuleSerialization35.deserialize(
                indexRule.getId(), wrap( SchemaRuleSerialization35.serialize( indexRule ) ) );

        // THEN
        assertEquals( indexRule.getId(), readIndexRule.getId() );
        assertEquals( indexRule.schema(), readIndexRule.schema() );
        assertEquals( indexRule, readIndexRule );
        assertEquals( indexRule.providerKey(), readIndexRule.providerKey() );
        assertEquals( indexRule.providerVersion(), readIndexRule.providerVersion() );
    }

    @Test
    void storeAndLoadCompositeSchemaRule() throws Exception
    {
        // GIVEN
        int[] propertyIds = {4, 5, 6, 7};
        StorageIndexReference indexRule = new DefaultStorageIndexReference( forLabel( 2, propertyIds ), PROVIDER_KEY, PROVIDER_VERSION, store.nextId(),
                Optional.empty(), false, null );

        // WHEN
        StorageIndexReference readIndexRule = (StorageIndexReference) SchemaRuleSerialization35.deserialize(
                indexRule.getId(), wrap( SchemaRuleSerialization35.serialize( indexRule ) ) );

        // THEN
        assertEquals( indexRule.getId(), readIndexRule.getId() );
        assertEquals( indexRule.schema(), readIndexRule.schema() );
        assertEquals( indexRule, readIndexRule );
        assertEquals( indexRule.providerKey(), readIndexRule.providerKey() );
        assertEquals( indexRule.providerVersion(), readIndexRule.providerVersion() );
    }

    @Test
    void storeAndLoadMultiTokenSchemaRule() throws Exception
    {
        // GIVEN
        int[] propertyIds = {4, 5, 6, 7};
        int[] entityTokens = {2, 3, 4};
        StorageIndexReference indexRule =
                new DefaultStorageIndexReference( fulltext( EntityType.RELATIONSHIP, IndexConfig.empty(), entityTokens, propertyIds ),
                        PROVIDER_KEY, PROVIDER_VERSION, store.nextId(), Optional.empty(), false, null );

        // WHEN
        StorageIndexReference readIndexRule =
                (StorageIndexReference) SchemaRuleSerialization35.deserialize( indexRule.getId(),
                        wrap( SchemaRuleSerialization35.serialize( indexRule ) ) );

        // THEN
        assertEquals( indexRule.getId(), readIndexRule.getId() );
        assertEquals( indexRule.schema(), readIndexRule.schema() );
        assertEquals( indexRule, readIndexRule );
        assertEquals( indexRule.providerKey(), readIndexRule.providerKey() );
        assertEquals( indexRule.providerVersion(), readIndexRule.providerVersion() );
    }

    @Test
    void storeAndLoad_Big_CompositeSchemaRule() throws Exception
    {
        // GIVEN
        StorageIndexReference indexRule =
                new DefaultStorageIndexReference( forLabel( 2, IntStream.range( 1, 200 ).toArray() ), PROVIDER_KEY, PROVIDER_VERSION, store.nextId(),
                        Optional.empty(), false, null );

        // WHEN
        StorageIndexReference readIndexRule = (StorageIndexReference) SchemaRuleSerialization35.deserialize(
                indexRule.getId(), wrap( SchemaRuleSerialization35.serialize( indexRule ) ) );

        // THEN
        assertEquals( indexRule.getId(), readIndexRule.getId() );
        assertEquals( indexRule.schema(), readIndexRule.schema() );
        assertEquals( indexRule, readIndexRule );
        assertEquals( indexRule.providerKey(), readIndexRule.providerKey() );
        assertEquals( indexRule.providerVersion(), readIndexRule.providerVersion() );
    }

    @Test
    void storeAndLoad_Big_CompositeMultiTokenSchemaRule() throws Exception
    {
        // GIVEN
        StorageIndexReference indexRule = new DefaultStorageIndexReference(
                fulltext( EntityType.RELATIONSHIP, IndexConfig.empty(), IntStream.range( 1, 200 ).toArray(), IntStream.range( 1, 200 ).toArray() ),
                PROVIDER_KEY, PROVIDER_VERSION, store.nextId(), Optional.empty(), false, null );

        // WHEN
        StorageIndexReference readIndexRule = (StorageIndexReference) SchemaRuleSerialization35.deserialize( indexRule.getId(),
                wrap( SchemaRuleSerialization35.serialize( indexRule ) ) );

        // THEN
        assertEquals( indexRule.getId(), readIndexRule.getId() );
        assertEquals( indexRule.schema(), readIndexRule.schema() );
        assertEquals( indexRule, readIndexRule );
        assertEquals( indexRule.providerKey(), readIndexRule.providerKey() );
        assertEquals( indexRule.providerVersion(), readIndexRule.providerVersion() );
    }

    @Test
    void storeAndLoadAllRules()
    {
        // GIVEN
        long indexId = store.nextId();
        long constraintId = store.nextId();
        Collection<SchemaRule> rules = Arrays.asList(
                uniqueIndexRule( indexId, constraintId, 2, 5, 3 ),
                constraintUniqueRule( constraintId, indexId, 2, 5, 3 ),
                indexRule( store.nextId(), 0, 5 ),
                indexRule( store.nextId(), 1, 6, 10, 99 ),
                constraintExistsRule( store.nextId(), 5, 1 )
        );

        for ( SchemaRule rule : rules )
        {
            storeRule( rule );
        }

        // WHEN
        SchemaStorage35 storage35 = new SchemaStorage35( store );
        Collection<SchemaRule> readRules = asCollection( storage35.getAll() );

        // THEN
        assertEquals( rules, readRules );
    }

    private long storeRule( SchemaRule rule )
    {
        Collection<DynamicRecord> records = store.allocateFrom( rule );
        for ( DynamicRecord record : records )
        {
            store.updateRecord( record );
        }
        return Iterables.first( records ).getId();
    }

    private static StorageIndexReference indexRule( long ruleId, int labelId, int... propertyIds )
    {
        return new DefaultStorageIndexReference( forLabel( labelId, propertyIds ), PROVIDER_KEY, PROVIDER_VERSION, ruleId, Optional.empty(), false, null );
    }

    private static StorageIndexReference uniqueIndexRule( long ruleId, long owningConstraint, int labelId, int... propertyIds )
    {
        return new DefaultStorageIndexReference( forLabel( labelId, propertyIds ), PROVIDER_KEY, PROVIDER_VERSION, ruleId, Optional.empty(), true, null );
    }

    private static ConstraintRule constraintUniqueRule( long ruleId, long ownedIndexId, int labelId, int... propertyIds )
    {
        return ConstraintRule.constraintRule( ruleId, ConstraintDescriptorFactory.uniqueForLabel( labelId, propertyIds ), ownedIndexId );
    }

    private static ConstraintRule constraintExistsRule( long ruleId, int labelId, int... propertyIds )
    {
        return ConstraintRule.constraintRule( ruleId, ConstraintDescriptorFactory.existsForLabel( labelId, propertyIds ) );
    }
}
