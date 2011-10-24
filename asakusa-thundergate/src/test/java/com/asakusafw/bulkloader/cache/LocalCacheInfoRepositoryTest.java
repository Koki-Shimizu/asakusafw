/**
 * Copyright 2011 Asakusa Framework Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.asakusafw.bulkloader.cache;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.asakusafw.bulkloader.common.BulkLoaderInitializer;
import com.asakusafw.bulkloader.common.DBConnection;
import com.asakusafw.bulkloader.testutil.UnitTestUtil;

/**
 * Test for {@link LocalCacheInfoRepository}.
 */
public class LocalCacheInfoRepositoryTest {

    private static List<String> properties = Arrays.asList(new String[]{"bulkloader-conf-db.properties"});
    private static String testJobflowId = "JOB_FLOW01";
    private static String testExecutionId = "JOB_FLOW01-001";

    /**
     * set up.
     * @throws Exception if failed
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        UnitTestUtil.setUpBeforeClass();
        UnitTestUtil.setUpEnv();
    }

    /**
     * clean up.
     * @throws Exception if failed
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        UnitTestUtil.tearDownEnv();
        UnitTestUtil.tearDownAfterClass();
    }

    /**
     * set up.
     * @throws Exception if failed
     */
    @Before
    public void setUp() throws Exception {
        BulkLoaderInitializer.initDBServer(testJobflowId, testExecutionId, properties, "target1");
        UnitTestUtil.setUpDB();
        UnitTestUtil.startUp();

        Connection connection = DBConnection.getConnection();
        Statement statement = null;
        try {
            statement = connection.createStatement();
            statement.execute("DROP TABLE IF EXISTS __TG_TEST1");
            statement.execute("DROP TABLE IF EXISTS __TG_TEST2");
            statement.execute("CREATE TABLE __TG_TEST1 ( VALUE DATETIME ) ENGINE=InnoDB");
            statement.execute("CREATE TABLE __TG_TEST2 ( VALUE VARCHAR(200) ) ENGINE=InnoDB");
        } finally {
            connection.close();
        }
    }

    /**
     * clean up.
     * @throws Exception if failed
     */
    @After
    public void tearDown() throws Exception {
        new ReleaseCacheLock().execute(null);
        UnitTestUtil.tearDown();
    }

    /**
     * Create.
     * @throws Exception if failed
     */
    @Test
    public void create() throws Exception {
        LocalCacheInfo info = new LocalCacheInfo(
                "testing",
                null,
                null,
                "__TG_TEST1",
                "/test/path");
        Connection connection = DBConnection.getConnection();
        try {
            LocalCacheInfoRepository repo = new LocalCacheInfoRepository(connection);
            repo.putCacheInfo(info);
            LocalCacheInfo restored = repo.getCacheInfo("testing");

            assertThat(restored.getId(), is(info.getId()));
            assertThat(restored.getLocalTimestamp(), is(not(nullValue())));
            assertThat(restored.getRemoteTimestamp(), is(nullValue()));
            assertThat(restored.getTableName(), is(info.getTableName()));
            assertThat(restored.getPath(), is(info.getPath()));
        } finally {
            connection.close();
        }
    }

    /**
     * Create but duplicated.
     * @throws Exception if failed
     */
    @Test
    public void create_duplicated() throws Exception {
        LocalCacheInfo info1 = new LocalCacheInfo(
                "testing",
                null,
                null,
                "__TG_TEST1",
                "/test/path1");
        LocalCacheInfo info2 = new LocalCacheInfo(
                "testing",
                null,
                calendar("2011-12-13 14:15:16"),
                "__TG_TEST2",
                "/test/path2");
        Connection connection = DBConnection.getConnection();
        try {
            LocalCacheInfoRepository repo = new LocalCacheInfoRepository(connection);
            repo.putCacheInfo(info1);
            Calendar timestamp = repo.putCacheInfo(info2);
            LocalCacheInfo restored = repo.getCacheInfo("testing");
            assertThat(restored.getId(), is(info2.getId()));
            assertThat(restored.getLocalTimestamp(), is(timestamp));
            assertThat(restored.getRemoteTimestamp(), is(info2.getRemoteTimestamp()));
            assertThat(restored.getTableName(), is(info2.getTableName()));
            assertThat(restored.getPath(), is(info2.getPath()));
        } finally {
            connection.close();
        }
    }

    /**
     * Update with blocking.
     * @throws Exception if failed
     */
    @Test
    public void put_with_blocking() throws Exception {
        LocalCacheInfo info = new LocalCacheInfo(
                "testing",
                calendar("2010-11-12 13:14:15"),
                calendar("2011-12-13 14:15:16"),
                "__TG_TEST1",
                "/test/path");
        Connection connection = DBConnection.getConnection();
        try {
            LocalCacheInfoRepository repo = new LocalCacheInfoRepository(connection);
            final CountDownLatch latch = new CountDownLatch(1);
            Future<Calendar> future = Executors.newFixedThreadPool(1).submit(new Callable<Calendar>() {
                @Override
                public Calendar call() throws Exception {
                    Connection inner = DBConnection.getConnection();
                    Statement statement = null;
                    ResultSet rs = null;
                    try {
                        statement = inner.createStatement();
                        statement.execute("LOCK TABLES __TG_TEST1 WRITE");
                        latch.countDown();
                        System.out.println("Waiting for testing lock mechanism");
                        Thread.sleep(3000);
                        rs = statement.executeQuery("SELECT NOW()");
                        assertThat(rs.next(), is(true));
                        Timestamp timestamp = rs.getTimestamp(1);
                        DBConnection.closeRs(rs);
                        statement.execute("UNLOCK TABLES");

                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(timestamp);
                        return calendar;
                    } finally {
                        DBConnection.closeRs(rs);
                        DBConnection.closeStmt(statement);
                        DBConnection.closeConn(inner);
                    }
                }
            });
            latch.await();
            Calendar updated = repo.putCacheInfo(info);

            assertThat(
                    tos(updated) + " >= " + tos(future.get()),
                    updated, greaterThanOrEqualTo(future.get()));
        } finally {
            connection.close();
        }
    }

    /**
     * Update with blocking other table.
     * @throws Exception if failed
     */
    @Test
    public void update_with_blocking_other() throws Exception {
        LocalCacheInfo info = new LocalCacheInfo(
                "testing",
                calendar("2010-11-12 13:14:15"),
                calendar("2011-12-13 14:15:16"),
                "__TG_TEST2",
                "/test/path");
        Connection connection = DBConnection.getConnection();
        try {
            LocalCacheInfoRepository repo = new LocalCacheInfoRepository(connection);
            final CountDownLatch latch = new CountDownLatch(1);
            Future<Calendar> future = Executors.newFixedThreadPool(1).submit(new Callable<Calendar>() {
                @Override
                public Calendar call() throws Exception {
                    Connection inner = DBConnection.getConnection();
                    Statement statement = null;
                    ResultSet rs = null;
                    try {
                        statement = inner.createStatement();
                        statement.execute("LOCK TABLES __TG_TEST1 WRITE");
                        latch.countDown();
                        System.out.println("Waiting for testing lock mechanism");
                        Thread.sleep(3000);
                        rs = statement.executeQuery("SELECT NOW()");
                        assertThat(rs.next(), is(true));
                        Timestamp timestamp = rs.getTimestamp(1);
                        DBConnection.closeRs(rs);
                        statement.execute("UNLOCK TABLES");

                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(timestamp);
                        return calendar;
                    } finally {
                        DBConnection.closeRs(rs);
                        DBConnection.closeStmt(statement);
                        DBConnection.closeConn(inner);
                    }
                }
            });
            latch.await();
            Calendar updated = repo.putCacheInfo(info);
            assertThat(
                    tos(updated) + " <= " + tos(future.get()),
                    updated, lessThanOrEqualTo(future.get()));
        } finally {
            connection.close();
        }
    }

    /**
     * Delete.
     * @throws Exception if failed
     */
    @Test
    public void delete() throws Exception {
        LocalCacheInfo info = new LocalCacheInfo(
                "testing",
                calendar("2010-11-12 13:14:15"),
                calendar("2011-12-13 14:15:16"),
                "__TG_TEST1",
                "/test/path");
        Connection connection = DBConnection.getConnection();
        try {
            LocalCacheInfoRepository repo = new LocalCacheInfoRepository(connection);
            repo.putCacheInfo(info);
            assertThat(repo.deleteCacheInfo("testing"), is(true));

            LocalCacheInfo restored = repo.getCacheInfo("testing");
            assertThat(restored, is(nullValue()));

            assertThat(repo.deleteCacheInfo("testing"), is(false));
        } finally {
            connection.close();
        }
    }

    /**
     * Delete but does not exist.
     * @throws Exception if failed
     */
    @Test
    public void delete_missing() throws Exception {
        Connection connection = DBConnection.getConnection();
        try {
            LocalCacheInfoRepository repo = new LocalCacheInfoRepository(connection);
            assertThat(repo.deleteCacheInfo("testing"), is(false));
        } finally {
            connection.close();
        }
    }

    /**
     * Lock cache.
     * @throws Exception if failed
     */
    @Test
    public void tryLock() throws Exception {
        Connection connection = DBConnection.getConnection();
        try {
            LocalCacheInfoRepository repo = new LocalCacheInfoRepository(connection);
            assertThat(repo.tryLock("testing", "a", "table"), is(true));
            assertThat(repo.tryLock("testing", "a", "table"), is(false));
        } finally {
            connection.close();
        }
    }

    /**
     * Release lock.
     * @throws Exception if failed
     */
    @Test
    public void releaseLock() throws Exception {
        Connection connection = DBConnection.getConnection();
        try {
            LocalCacheInfoRepository repo = new LocalCacheInfoRepository(connection);
            assertThat(repo.tryLock("testing1", "a", "table1"), is(true));
            assertThat(repo.tryLock("testing2", "b", "table1"), is(true));
            assertThat(repo.tryLock("testing1", "c", "table2"), is(true));

            repo.releaseLock("testing1");
            assertThat(repo.tryLock("testing1", "a", "table1"), is(true));
            assertThat(repo.tryLock("testing2", "b", "table1"), is(false));
            assertThat(repo.tryLock("testing1", "c", "table2"), is(true));
        } finally {
            connection.close();
        }
    }

    /**
     * Test method for {@link com.asakusafw.bulkloader.cache.LocalCacheInfoRepository#releaseAllLock()}.
     * @throws Exception if failed
     */
    @Test
    public void releaseAllLock() throws Exception {
        Connection connection = DBConnection.getConnection();
        try {
            LocalCacheInfoRepository repo = new LocalCacheInfoRepository(connection);
            assertThat(repo.tryLock("testing1", "a", "table1"), is(true));
            assertThat(repo.tryLock("testing2", "b", "table1"), is(true));
            assertThat(repo.tryLock("testing1", "c", "table2"), is(true));

            repo.releaseAllLock();
            assertThat(repo.tryLock("testing1", "a", "table1"), is(true));
            assertThat(repo.tryLock("testing2", "b", "table1"), is(true));
            assertThat(repo.tryLock("testing1", "c", "table2"), is(true));
        } finally {
            connection.close();
        }
    }

    private String tos(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(calendar.getTime());
    }

    private Calendar calendar(String string) {
        Date date;
        try {
            date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(string);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }
}
