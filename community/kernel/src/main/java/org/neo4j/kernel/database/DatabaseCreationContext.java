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
package org.neo4j.kernel.database;

import java.util.function.Function;

import org.neo4j.common.TokenNameLookup;
import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.watcher.DatabaseLayoutWatcher;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.pagecache.IOLimiter;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.tracing.cursor.context.VersionContextSupplier;
import org.neo4j.kernel.availability.DatabaseAvailability;
import org.neo4j.kernel.availability.DatabaseAvailabilityGuard;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.extension.ExtensionFactory;
import org.neo4j.kernel.impl.api.CommitProcessFactory;
import org.neo4j.kernel.impl.api.SchemaWriteGuard;
import org.neo4j.kernel.impl.constraints.ConstraintSemantics;
import org.neo4j.kernel.impl.core.TokenHolders;
import org.neo4j.kernel.impl.coreapi.CoreAPIAvailabilityGuard;
import org.neo4j.kernel.impl.factory.AccessCapability;
import org.neo4j.kernel.impl.factory.DatabaseInfo;
import org.neo4j.kernel.impl.factory.GraphDatabaseFacade;
import org.neo4j.kernel.impl.locking.Locks;
import org.neo4j.kernel.impl.locking.StatementLocksFactory;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.impl.query.QueryEngineProvider;
import org.neo4j.kernel.impl.store.id.IdController;
import org.neo4j.kernel.impl.store.id.IdGeneratorFactory;
import org.neo4j.kernel.impl.transaction.TransactionHeaderInformationFactory;
import org.neo4j.kernel.impl.transaction.TransactionMonitor;
import org.neo4j.kernel.impl.transaction.log.checkpoint.StoreCopyCheckPointMutex;
import org.neo4j.kernel.impl.util.collection.CollectionsFactorySupplier;
import org.neo4j.kernel.internal.DatabaseEventHandlers;
import org.neo4j.kernel.internal.DatabaseHealth;
import org.neo4j.kernel.internal.TransactionEventHandlers;
import org.neo4j.kernel.monitoring.Monitors;
import org.neo4j.kernel.monitoring.tracing.Tracers;
import org.neo4j.logging.internal.LogService;
import org.neo4j.scheduler.JobScheduler;
import org.neo4j.storageengine.migration.DatabaseMigratorFactory;
import org.neo4j.time.SystemNanoClock;

public interface DatabaseCreationContext
{
    String getDatabaseName();

    DatabaseLayout getDatabaseLayout();

    Config getConfig();

    IdGeneratorFactory getIdGeneratorFactory();

    LogService getLogService();

    JobScheduler getScheduler();

    TokenNameLookup getTokenNameLookup();

    DependencyResolver getGlobalDependencies();

    TokenHolders getTokenHolders();

    Locks getLocks();

    StatementLocksFactory getStatementLocksFactory();

    SchemaWriteGuard getSchemaWriteGuard();

    TransactionEventHandlers getTransactionEventHandlers();

    FileSystemAbstraction getFs();

    TransactionMonitor getTransactionMonitor();

    DatabaseHealth getDatabaseHealth();

    TransactionHeaderInformationFactory getTransactionHeaderInformationFactory();

    CommitProcessFactory getCommitProcessFactory();

    PageCache getPageCache();

    ConstraintSemantics getConstraintSemantics();

    Monitors getMonitors();

    Tracers getTracers();

    Procedures getProcedures();

    IOLimiter getIoLimiter();

    DatabaseAvailabilityGuard getDatabaseAvailabilityGuard();

    CoreAPIAvailabilityGuard getCoreAPIAvailabilityGuard();

    SystemNanoClock getClock();

    AccessCapability getAccessCapability();

    StoreCopyCheckPointMutex getStoreCopyCheckPointMutex();

    IdController getIdController();

    DatabaseInfo getDatabaseInfo();

    VersionContextSupplier getVersionContextSupplier();

    CollectionsFactorySupplier getCollectionsFactorySupplier();

    Iterable<ExtensionFactory<?>> getExtensionFactories();

    Function<DatabaseLayout,DatabaseLayoutWatcher> getWatcherServiceFactory();

    GraphDatabaseFacade getFacade();

    Iterable<QueryEngineProvider> getEngineProviders();

    DatabaseAvailability getDatabaseAvailability();

    DatabaseEventHandlers getEventHandlers();

    DatabaseMigratorFactory getDatabaseMigratorFactory();
}
