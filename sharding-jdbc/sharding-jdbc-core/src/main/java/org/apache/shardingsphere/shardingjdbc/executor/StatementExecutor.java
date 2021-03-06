/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shardingjdbc.executor;

import org.apache.shardingsphere.shardingjdbc.executor.callback.RuleExecuteExecutorCallback;
import org.apache.shardingsphere.shardingjdbc.executor.callback.RuleExecuteQueryExecutorCallback;
import org.apache.shardingsphere.shardingjdbc.executor.callback.RuleExecuteUpdateExecutorCallback;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.context.RuntimeContext;
import org.apache.shardingsphere.sharding.spi.ShardingSphereServiceLoader;
import org.apache.shardingsphere.sharding.spi.order.OrderedSPIRegistry;
import org.apache.shardingsphere.sql.parser.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.underlying.common.metadata.refresh.MetaDataRefreshStrategy;
import org.apache.shardingsphere.underlying.common.metadata.refresh.MetaDataRefreshStrategyFactory;
import org.apache.shardingsphere.underlying.common.metadata.schema.RuleSchemaMetaDataLoader;
import org.apache.shardingsphere.underlying.common.rule.DataNodeRoutedRule;
import org.apache.shardingsphere.underlying.common.rule.ShardingSphereRule;
import org.apache.shardingsphere.underlying.executor.kernel.InputGroup;
import org.apache.shardingsphere.underlying.executor.sql.ConnectionMode;
import org.apache.shardingsphere.underlying.executor.sql.QueryResult;
import org.apache.shardingsphere.underlying.executor.sql.execute.jdbc.StatementExecuteUnit;
import org.apache.shardingsphere.underlying.executor.sql.execute.jdbc.executor.ExecutorExceptionHandler;
import org.apache.shardingsphere.underlying.executor.sql.execute.jdbc.executor.SQLExecutor;
import org.apache.shardingsphere.underlying.executor.sql.execute.jdbc.executor.SQLExecutorCallback;
import org.apache.shardingsphere.underlying.executor.sql.execute.jdbc.executor.impl.DefaultSQLExecutorCallback;
import org.apache.shardingsphere.underlying.executor.sql.execute.jdbc.queryresult.MemoryQueryResult;
import org.apache.shardingsphere.underlying.executor.sql.execute.jdbc.queryresult.StreamQueryResult;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Statement executor.
 */
public final class StatementExecutor {
    
    static {
        ShardingSphereServiceLoader.register(RuleExecuteQueryExecutorCallback.class);
        ShardingSphereServiceLoader.register(RuleExecuteUpdateExecutorCallback.class);
        ShardingSphereServiceLoader.register(RuleExecuteExecutorCallback.class);
    }
    
    private final Map<String, DataSource> dataSourceMap;
    
    private final RuntimeContext runtimeContext;
    
    private final SQLExecutor sqlExecutor;
    
    public StatementExecutor(final Map<String, DataSource> dataSourceMap, final RuntimeContext runtimeContext, final SQLExecutor sqlExecutor) {
        this.dataSourceMap = dataSourceMap;
        this.runtimeContext = runtimeContext;
        this.sqlExecutor = sqlExecutor;
    }
    
    /**
     * Execute query.
     * 
     * @param inputGroups input groups
     * @return result set list
     * @throws SQLException SQL exception
     */
    @SuppressWarnings("unchecked")
    public List<QueryResult> executeQuery(final Collection<InputGroup<StatementExecuteUnit>> inputGroups) throws SQLException {
        boolean isExceptionThrown = ExecutorExceptionHandler.isExceptionThrown();
        SQLExecutorCallback<QueryResult> sqlExecutorCallback = getExecuteQueryExecutorCallback(new DefaultSQLExecutorCallback<QueryResult>(runtimeContext.getDatabaseType(), isExceptionThrown) {
            
            @Override
            protected QueryResult executeSQL(final String sql, final Statement statement, final ConnectionMode connectionMode) throws SQLException {
                return createQueryResult(sql, statement, connectionMode);
            }
        });
        return sqlExecutor.execute(inputGroups, sqlExecutorCallback);
    }
    
    private SQLExecutorCallback<QueryResult> getExecuteQueryExecutorCallback(final DefaultSQLExecutorCallback callback) {
        Map<ShardingSphereRule, RuleExecuteQueryExecutorCallback> callbackMap = OrderedSPIRegistry.getRegisteredServices(runtimeContext.getRules(), RuleExecuteQueryExecutorCallback.class);
        return callbackMap.isEmpty() ? callback : callbackMap.values().iterator().next();
    }
    
    private QueryResult createQueryResult(final String sql, final Statement statement, final ConnectionMode connectionMode) throws SQLException {
        ResultSet resultSet = statement.executeQuery(sql);
        return ConnectionMode.MEMORY_STRICTLY == connectionMode ? new StreamQueryResult(resultSet) : new MemoryQueryResult(resultSet);
    }
    
    /**
     * Execute update.
     * 
     * @param inputGroups input groups
     * @param sqlStatementContext SQL statement context
     * @return effected records count
     * @throws SQLException SQL exception
     */
    public int executeUpdate(final Collection<InputGroup<StatementExecuteUnit>> inputGroups, final SQLStatementContext sqlStatementContext) throws SQLException {
        return executeUpdate(inputGroups, Statement::executeUpdate, sqlStatementContext);
    }
    
    /**
     * Execute update with auto generated keys.
     * 
     * @param inputGroups input groups
     * @param sqlStatementContext SQL statement context
     * @param autoGeneratedKeys auto generated keys' flag
     * @return effected records count
     * @throws SQLException SQL exception
     */
    public int executeUpdate(final Collection<InputGroup<StatementExecuteUnit>> inputGroups, final SQLStatementContext sqlStatementContext, final int autoGeneratedKeys) throws SQLException {
        return executeUpdate(inputGroups, (statement, sql) -> statement.executeUpdate(sql, autoGeneratedKeys), sqlStatementContext);
    }
    
    /**
     * Execute update with column indexes.
     *
     * @param inputGroups input groups
     * @param sqlStatementContext SQL statement context
     * @param columnIndexes column indexes
     * @return effected records count
     * @throws SQLException SQL exception
     */
    public int executeUpdate(final Collection<InputGroup<StatementExecuteUnit>> inputGroups, final SQLStatementContext sqlStatementContext, final int[] columnIndexes) throws SQLException {
        return executeUpdate(inputGroups, (statement, sql) -> statement.executeUpdate(sql, columnIndexes), sqlStatementContext);
    }
    
    /**
     * Execute update with column names.
     *
     * @param inputGroups input groups
     * @param sqlStatementContext SQL statement context
     * @param columnNames column names
     * @return effected records count
     * @throws SQLException SQL exception
     */
    public int executeUpdate(final Collection<InputGroup<StatementExecuteUnit>> inputGroups, final SQLStatementContext sqlStatementContext, final String[] columnNames) throws SQLException {
        return executeUpdate(inputGroups, (statement, sql) -> statement.executeUpdate(sql, columnNames), sqlStatementContext);
    }
    
    @SuppressWarnings("unchecked")
    private int executeUpdate(final Collection<InputGroup<StatementExecuteUnit>> inputGroups, final Updater updater, final SQLStatementContext sqlStatementContext) throws SQLException {
        final boolean isExceptionThrown = ExecutorExceptionHandler.isExceptionThrown();
        SQLExecutorCallback sqlExecutorCallback = getExecuteUpdateExecutorCallback(new DefaultSQLExecutorCallback<Integer>(runtimeContext.getDatabaseType(), isExceptionThrown) {
            
            @Override
            protected Integer executeSQL(final String sql, final Statement statement, final ConnectionMode connectionMode) throws SQLException {
                return updater.executeUpdate(statement, sql);
            }
        });
        List<Integer> results = sqlExecutor.execute(inputGroups, sqlExecutorCallback);
        refreshTableMetaData(runtimeContext, sqlStatementContext);
        if (isNeedAccumulate(runtimeContext.getRules().stream().filter(rule -> rule instanceof DataNodeRoutedRule).collect(Collectors.toList()), sqlStatementContext)) {
            return accumulate(results);
        }
        return null == results.get(0) ? 0 : results.get(0);
    }
    
    private SQLExecutorCallback<Integer> getExecuteUpdateExecutorCallback(final DefaultSQLExecutorCallback callback) {
        Map<ShardingSphereRule, RuleExecuteUpdateExecutorCallback> callbackMap = OrderedSPIRegistry.getRegisteredServices(runtimeContext.getRules(), RuleExecuteUpdateExecutorCallback.class);
        return callbackMap.isEmpty() ? callback : callbackMap.values().iterator().next();
    }
    
    private boolean isNeedAccumulate(final Collection<ShardingSphereRule> rules, final SQLStatementContext sqlStatementContext) {
        return rules.stream().anyMatch(each -> ((DataNodeRoutedRule) each).isNeedAccumulate(sqlStatementContext.getTablesContext().getTableNames()));
    }
    
    private int accumulate(final List<Integer> results) {
        int result = 0;
        for (Integer each : results) {
            result += null == each ? 0 : each;
        }
        return result;
    }
    
    /**
     * Execute SQL.
     *
     * @param inputGroups input groups
     * @param sqlStatementContext SQL statement context
     * @return return true if is DQL, false if is DML
     * @throws SQLException SQL exception
     */
    public boolean execute(final Collection<InputGroup<StatementExecuteUnit>> inputGroups, final SQLStatementContext sqlStatementContext) throws SQLException {
        return execute(inputGroups, Statement::execute, sqlStatementContext);
    }
    
    /**
     * Execute SQL with auto generated keys.
     *
     * @param inputGroups input groups
     * @param sqlStatementContext SQL statement context
     * @param autoGeneratedKeys auto generated keys' flag
     * @return return true if is DQL, false if is DML
     * @throws SQLException SQL exception
     */
    public boolean execute(final Collection<InputGroup<StatementExecuteUnit>> inputGroups, final SQLStatementContext sqlStatementContext, final int autoGeneratedKeys) throws SQLException {
        return execute(inputGroups, (statement, sql) -> statement.execute(sql, autoGeneratedKeys), sqlStatementContext);
    }
    
    /**
     * Execute SQL with column indexes.
     *
     * @param inputGroups input groups
     * @param sqlStatementContext SQL statement context
     * @param columnIndexes column indexes
     * @return return true if is DQL, false if is DML
     * @throws SQLException SQL exception
     */
    public boolean execute(final Collection<InputGroup<StatementExecuteUnit>> inputGroups, final SQLStatementContext sqlStatementContext, final int[] columnIndexes) throws SQLException {
        return execute(inputGroups, (statement, sql) -> statement.execute(sql, columnIndexes), sqlStatementContext);
    }
    
    /**
     * Execute SQL with column names.
     *
     * @param inputGroups input groups
     * @param sqlStatementContext SQL statement context
     * @param columnNames column names
     * @return return true if is DQL, false if is DML
     * @throws SQLException SQL exception
     */
    public boolean execute(final Collection<InputGroup<StatementExecuteUnit>> inputGroups, final SQLStatementContext sqlStatementContext, final String[] columnNames) throws SQLException {
        return execute(inputGroups, (statement, sql) -> statement.execute(sql, columnNames), sqlStatementContext);
    }
    
    @SuppressWarnings("unchecked")
    private boolean execute(final Collection<InputGroup<StatementExecuteUnit>> inputGroups, final Executor executor, final SQLStatementContext sqlStatementContext) throws SQLException {
        boolean isExceptionThrown = ExecutorExceptionHandler.isExceptionThrown();
        SQLExecutorCallback sqlExecutorCallback = getExecuteExecutorCallback(new DefaultSQLExecutorCallback<Boolean>(runtimeContext.getDatabaseType(), isExceptionThrown) {
            
            @Override
            protected Boolean executeSQL(final String sql, final Statement statement, final ConnectionMode connectionMode) throws SQLException {
                return executor.execute(statement, sql);
            }
        });
        List<Boolean> result = sqlExecutor.execute(inputGroups, sqlExecutorCallback);
        refreshTableMetaData(runtimeContext, sqlStatementContext);
        if (null == result || result.isEmpty() || null == result.get(0)) {
            return false;
        }
        return result.get(0);
    }
    
    private SQLExecutorCallback<Boolean> getExecuteExecutorCallback(final DefaultSQLExecutorCallback callback) {
        Map<ShardingSphereRule, RuleExecuteExecutorCallback> callbackMap = OrderedSPIRegistry.getRegisteredServices(runtimeContext.getRules(), RuleExecuteExecutorCallback.class);
        return callbackMap.isEmpty() ? callback : callbackMap.values().iterator().next();
    }
    
    @SuppressWarnings("unchecked")
    private void refreshTableMetaData(final RuntimeContext runtimeContext, final SQLStatementContext sqlStatementContext) throws SQLException {
        if (null == sqlStatementContext) {
            return;
        }
        Optional<MetaDataRefreshStrategy> refreshStrategy = MetaDataRefreshStrategyFactory.newInstance(sqlStatementContext);
        if (refreshStrategy.isPresent()) {
            RuleSchemaMetaDataLoader metaDataLoader = new RuleSchemaMetaDataLoader(runtimeContext.getRules());
            refreshStrategy.get().refreshMetaData(runtimeContext.getMetaData(), runtimeContext.getDatabaseType(), dataSourceMap, sqlStatementContext,
                tableName -> metaDataLoader.load(runtimeContext.getDatabaseType(), dataSourceMap, tableName, runtimeContext.getProperties()));
        }
    }
    
    private interface Updater {
        
        int executeUpdate(Statement statement, String sql) throws SQLException;
    }
    
    private interface Executor {
        
        boolean execute(Statement statement, String sql) throws SQLException;
    }
}

