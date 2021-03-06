/**
 * Copyright 2011-2012 Asakusa Framework Team.
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
package com.asakusafw.compiler.util.tester;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Writable;
import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.asakusafw.compiler.common.JavaName;
import com.asakusafw.compiler.flow.FlowCompilerOptions;
import com.asakusafw.compiler.flow.FlowDescriptionDriver;
import com.asakusafw.compiler.flow.JobFlowDriver;
import com.asakusafw.compiler.flow.Location;
import com.asakusafw.compiler.flow.jobflow.JobflowModel.Export;
import com.asakusafw.compiler.flow.jobflow.JobflowModel.Import;
import com.asakusafw.compiler.testing.BatchInfo;
import com.asakusafw.compiler.testing.DirectBatchCompiler;
import com.asakusafw.compiler.testing.DirectExporterDescription;
import com.asakusafw.compiler.testing.DirectFlowCompiler;
import com.asakusafw.compiler.testing.DirectImporterDescription;
import com.asakusafw.compiler.testing.JobflowInfo;
import com.asakusafw.compiler.testing.StageInfo;
import com.asakusafw.runtime.configuration.FrameworkDeployer;
import com.asakusafw.runtime.io.ModelInput;
import com.asakusafw.runtime.io.ModelOutput;
import com.asakusafw.runtime.stage.AbstractCleanupStageClient;
import com.asakusafw.runtime.stage.StageConstants;
import com.asakusafw.runtime.util.VariableTable;
import com.asakusafw.runtime.util.VariableTable.RedefineStrategy;
import com.asakusafw.utils.collections.Lists;
import com.asakusafw.vocabulary.batch.BatchDescription;
import com.asakusafw.vocabulary.external.ExporterDescription;
import com.asakusafw.vocabulary.external.ImporterDescription;
import com.asakusafw.vocabulary.external.ImporterDescription.DataSize;
import com.asakusafw.vocabulary.flow.FlowDescription;
import com.asakusafw.vocabulary.flow.In;
import com.asakusafw.vocabulary.flow.Out;
import com.asakusafw.vocabulary.flow.graph.FlowGraph;

/**
 * コンパイラのテストを行う。
 */
public class CompilerTester implements TestRule {

    static final Logger LOG = LoggerFactory.getLogger(CompilerTester.class);

    /**
     * Hadoop driver.
     */
    protected final HadoopDriver hadoopDriver;

    /**
     * Deployes framework.
     */
    protected final FrameworkDeployer frameworkDeployer;

    final FlowDescriptionDriver flow;

    Class<?> testClass;

    String testName;

    private final VariableTable variables;

    private final FlowCompilerOptions options;

    private final List<File> libraries;

    /**
     * Creates a new instance.
     */
    public CompilerTester() {
        this(true);
    }

    /**
     * Creates a new instance.
     * @param createFramework creates framework structure from src/.../dist.
     */
    public CompilerTester(boolean createFramework) {
        this.hadoopDriver = HadoopDriver.createInstance();
        this.frameworkDeployer = new FrameworkDeployer(createFramework);
        this.flow = new FlowDescriptionDriver();
        this.testClass = getClass();
        this.testName = "unknown";
        this.variables = new VariableTable(RedefineStrategy.ERROR);
        this.options = new FlowCompilerOptions();
        this.libraries = new ArrayList<File>();
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        Statement stmt = new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Assume.assumeNotNull(hadoopDriver);
                try {
                    testClass = description.getTestClass();
                    testName = MessageFormat.format(
                            "{0}_{1}",
                            description.getTestClass().getSimpleName(),
                            description.getMethodName().replaceAll("\\W", "_"));
                    hadoopDriver.setLogger(LoggerFactory.getLogger(testClass));
                    hadoopDriver.clean();
                    configure(description);
                    base.evaluate();
                } finally {
                    hadoopDriver.close();
                }
            }
        };
        return frameworkDeployer.apply(stmt, description);
    }

    /**
     * Configures this object.
     * @param description test description
     */
    protected void configure(Description description) {
        return;
    }

    /**
     * Returns the variable table for batch arguments.
     * Clients can modify returned object.
     * @return the variables
     */
    public VariableTable variables() {
        return variables;
    }

    /**
     * Returns the compiler options.
     * Clients can modify returned object.
     * @return compiler options
     */
    public FlowCompilerOptions options() {
        return options;
    }

    /**
     * Returns hadoop configuration.
     * Clients can modify returned object.
     * @return hadoop configuration
     */
    public Configuration configuration() {
        return hadoopDriver.getConfiguration();
    }

    /**
     * Returns runtime libraries.
     * Clients can modify returned list.
     * @return framework classes
     */
    public List<File> libraries() {
        return libraries;
    }

    /**
     * Returns current framework deployer.
     * @return framework deployer.
     */
    public FrameworkDeployer framework() {
        return frameworkDeployer;
    }

    /**
     * 指定のフロー記述を元にプログラムを生成し、実行する。
     * @param description 対象のフロー記述
     * @return 実行に成功した場合のみ{@code true}
     * @throws IOException コンパイルや実行に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public boolean runFlow(FlowDescription description) throws IOException {
        if (description == null) {
            throw new IllegalArgumentException("description must not be null"); //$NON-NLS-1$
        }
        return run(compileFlow(description));
    }

    /**
     * 指定のフロー記述を解析してフローグラフの形式で返す。
     * @param description 対象のフロー記述
     * @return 解析結果のフローグラフ
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public FlowGraph analyzeFlow(FlowDescription description) {
        if (description == null) {
            throw new IllegalArgumentException("description must not be null"); //$NON-NLS-1$
        }
        return flow.createFlowGraph(description);
    }

    /**
     * 指定のフロー記述を元にプログラムを生成する。
     * @param description 対象のフロー記述
     * @return コンパイル結果
     * @throws IOException コンパイルに失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public JobflowInfo compileFlow(FlowDescription description) throws IOException {
        if (description == null) {
            throw new IllegalArgumentException("description must not be null"); //$NON-NLS-1$
        }
        FlowGraph graph = flow.createFlowGraph(description);
        List<File> classPath = buildClassPath(description.getClass());
        return DirectFlowCompiler.compile(
                graph,
                "testing",
                description.getClass().getName(),
                "com.example",
                hadoopDriver.toPath(path("runtime", "stages")),
                new File("target/localwork", testName),
                classPath,
                getClass().getClassLoader(),
                options);
    }

    /**
     * 指定のジョブフロークラスを元にプログラムを生成する。
     * @param description 対象のフロー記述
     * @return コンパイル結果
     * @throws IOException コンパイルに失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public JobflowInfo compileJobflow(Class<? extends FlowDescription> description) throws IOException {
        JobFlowDriver driver = JobFlowDriver.analyze(description);
        assertThat(driver.getDiagnostics().toString(), driver.hasError(), is(false));
        List<File> classPath = buildClassPath(description);
        JobflowInfo info = DirectFlowCompiler.compile(
                driver.getJobFlowClass().getGraph(),
                "testing",
                driver.getJobFlowClass().getConfig().name(),
                "com.example",
                hadoopDriver.toPath(path("runtime", "jobflow")),
                new File("target/localwork", testName),
                classPath,
                getClass().getClassLoader(),
                options);
        return info;
    }

    /**
     * 指定のジョブフロークラスを元にプログラムを生成し、実行する。
     * @param description 対象のフロー記述
     * @return 実行に成功した場合のみ{@code true}
     * @throws IOException コンパイルや実行に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public boolean runJobflow(Class<? extends FlowDescription> description) throws IOException {
        if (description == null) {
            throw new IllegalArgumentException("description must not be null"); //$NON-NLS-1$
        }
        JobflowInfo info = compileJobflow(description);
        return run(info);
    }

    /**
     * 指定のバッチ記述を元にプログラムを生成する。
     * @param description 対象のバッチ記述
     * @return 実行に成功した場合のみ{@code true}
     * @throws IOException コンパイルに失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public BatchInfo compileBatch(Class<? extends BatchDescription> description) throws IOException {
        if (description == null) {
            throw new IllegalArgumentException("description must not be null"); //$NON-NLS-1$
        }
        List<File> classPath = buildClassPath(description);
        BatchInfo info = DirectBatchCompiler.compile(
                description,
                "com.example",
                hadoopDriver.toPath(path("runtime", "batch")),
                new File("target/CompilerTester/" + testName + "/output"),
                new File("target/CompilerTester/" + testName + "/build"),
                classPath,
                getClass().getClassLoader(),
                options);
        return info;
    }

    /**
     * 指定のバッチクラスを元にプログラムを生成し、実行する。
     * @param description 対象のバッチ記述
     * @return 実行に成功した場合のみ{@code true}
     * @throws IOException コンパイルや実行に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public boolean runBatch(Class<? extends BatchDescription> description) throws IOException {
        if (description == null) {
            throw new IllegalArgumentException("description must not be null"); //$NON-NLS-1$
        }
        return run(compileBatch(description));
    }

    /**
     * 指定のバッチを実行する。
     * @param info 対象のバッチ情報
     * @return 実行に成功した場合のみ{@code true}
     * @throws IOException 実行に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public boolean run(BatchInfo info) throws IOException {
        if (info == null) {
            throw new IllegalArgumentException("info must not be null"); //$NON-NLS-1$
        }
        for (JobflowInfo jobflow : info.getJobflows()) {
            boolean succeed = run(jobflow);
            if (succeed == false) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs the specified jobflow.
     * @param info target jobflow information
     * @return {@code true} iff execution was succeeded
     * @throws IOException if failed to execute job
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public boolean run(JobflowInfo info) throws IOException {
        return run(info, true, true);
    }

    /**
     * Runs the specified jobflow except cleanup.
     * @param info target jobflow information
     * @return {@code true} iff execution was succeeded
     * @throws IOException if failed to execute job
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public boolean runStages(JobflowInfo info) throws IOException {
        return run(info, true, false);
    }

    private boolean run(JobflowInfo info, boolean stages, boolean cleanup) throws IOException {
        if (info == null) {
            throw new IllegalArgumentException("info must not be null"); //$NON-NLS-1$
        }
        File confFile = frameworkDeployer.getCoreConfigurationFile();
        if (confFile == null) {
            LOG.info("execute hadoop with configuration file");
        } else {
            LOG.warn("execute hadoop with no configuration file (missing: {})", confFile.getAbsolutePath());
        }

        Map<String, String> definitions = new HashMap<String, String>();
        definitions.put(StageConstants.PROP_USER, System.getProperty("user.name"));
        definitions.put(StageConstants.PROP_EXECUTION_ID, UUID.randomUUID().toString());
        definitions.put(StageConstants.PROP_ASAKUSA_BATCH_ARGS, variables.toSerialString());

        List<File> libjars = new ArrayList<File>();
        libjars.add(info.getPackageFile());
        libjars.addAll(libraries);

        if (stages) {
            if (executeStage(info, confFile, definitions, libjars) == false) {
                return false;
            }
        }
        if (cleanup) {
            if (executeCleanup(info, confFile, definitions, libjars) == false) {
                return false;
            }
        }
        return true;
    }

    private boolean executeStage(
            JobflowInfo info,
            File confFile,
            Map<String, String> definitions,
            List<File> libjars) throws IOException {
        assert info != null;
        assert definitions != null;
        assert libjars != null;
        for (StageInfo stage : info.getStages()) {
            boolean succeed = hadoopDriver.runJob(
                    frameworkDeployer.getCoreRuntimeLibrary(),
                    libjars,
                    stage.getClassName(),
                    confFile,
                    definitions);
            if (succeed == false) {
                return false;
            }
        }
        return true;
    }

    private boolean executeCleanup(
            JobflowInfo info,
            File confFile,
            Map<String, String> definitions,
            List<File> libjars) throws IOException {
        assert info != null;
        assert definitions != null;
        assert libjars != null;
        if (info.getStages().isEmpty() == false) {
            boolean succeed = hadoopDriver.runJob(
                    frameworkDeployer.getCoreRuntimeLibrary(),
                    libjars,
                    AbstractCleanupStageClient.IMPLEMENTATION,
                    confFile,
                    definitions);
            if (succeed == false) {
                return false;
            }
        }
        return true;
    }

    private List<File> buildClassPath(Class<?>... libraryClasses) {
        List<File> classPath = Lists.create();
        classPath.add(findClassPathFromClass(testClass));
        for (Class<?> libraryClass : libraryClasses) {
            classPath.add(findClassPathFromClass(libraryClass));
        }
        return classPath;
    }

    private File findClassPathFromClass(Class<?> aClass) {
        assert aClass != null;
        File path = DirectFlowCompiler.toLibraryPath(aClass);
        assertThat(aClass.getName(), path, not(nullValue()));
        return path;
    }

    /**
     * フローへの入力を構築するオブジェクトを返す。
     * @param <T> 入力するデータの種類
     * @param type 入力するデータの種類
     * @param name 入力の名前
     * @return 生成したオブジェクト
     * @throws IOException 入力に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public <T extends Writable> TestInput<T> input(
            Class<T> type,
            String name) throws IOException {
        return input(type, name, DataSize.UNKNOWN);
    }

    /**
     * フローへの入力を構築するオブジェクトを返す。
     * @param <T> 入力するデータの種類
     * @param type 入力するデータの種類
     * @param name 入力の名前
     * @param dataSize 入力するデータに関するサイズのヒント
     * @return 生成したオブジェクト
     * @throws IOException 入力に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public <T extends Writable> TestInput<T> input(
            Class<T> type,
            String name,
            DataSize dataSize) throws IOException {
        Location path = hadoopDriver.toPath(path("input", JavaName.of(name).toMemberName()));
        return new TestInput<T>(type, name, path, dataSize);
    }

    /**
     * フローへの入力を構築するオブジェクトを返す。
     * @param <T> 入力するデータの種類
     * @param importer 利用するインポーター
     * @param name 入力の名前
     * @return 生成したオブジェクト
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public <T> In<T> input(
            String name,
            ImporterDescription importer) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null"); //$NON-NLS-1$
        }
        if (importer == null) {
            throw new IllegalArgumentException("importer must not be null"); //$NON-NLS-1$
        }
        return flow.createIn(name, importer);
    }

    /**
     * フローへの入力を構築するオブジェクトを返す。
     * @param <T> 入力するデータの種類
     * @param exporter 利用するエクスポーター
     * @param name 入力の名前
     * @return 生成したオブジェクト
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public <T> Out<T> output(
            String name,
            ExporterDescription exporter) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null"); //$NON-NLS-1$
        }
        if (exporter == null) {
            throw new IllegalArgumentException("exporter must not be null"); //$NON-NLS-1$
        }
        return flow.createOut(name, exporter);
    }

    /**
     * フローからの出力を構築するオブジェクトを返す。
     * @param <T> 出力するデータの種類
     * @param type 出力するデータの種類
     * @param name 出力の名前
     * @return 生成したオブジェクト
     * @throws IOException 出力に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public <T extends Writable> TestOutput<T> output(
            Class<T> type,
            String name) throws IOException {
        Location path = hadoopDriver.toPath(testName, "output", name).asPrefix();
        return new TestOutput<T>(type, name, path);
    }

    /**
     * 指定位置のファイルを開き、モデルオブジェクトの列をシーケンスファイルとして書き出す。
     * @param <T> モデルオブジェクトの種類
     * @param type モデルオブジェクトの種類
     * @param location 対象の位置
     * @return 出力用のオブジェクト
     * @throws IOException 出力の作成に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public <T extends Writable> ModelOutput<T> openOutput(
            Class<T> type,
            Location location) throws IOException {
        return hadoopDriver.openOutput(type, location);
    }

    /**
     * 対象のインポーターが書き出す先をシーケンスファイルとして書き出す。
     * @param <T> モデルオブジェクトの種類
     * @param type モデルオブジェクトの種類
     * @param importer 対象のインポーター
     * @return 出力用のオブジェクト
     * @throws IOException 出力の作成に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public <T extends Writable> ModelOutput<T> openOutput(
            Class<T> type,
            Import importer) throws IOException {
        Iterator<Location> iter = importer.getInputInfo().getLocations().iterator();
        assert iter.hasNext();
        Location location = iter.next();
        return hadoopDriver.openOutput(type, location);
    }

    /**
     * 指定位置のファイルを開き、シーケンスファイルをモデルオブジェクトの列として読みだす。
     * @param <T> モデルオブジェクトの種類
     * @param type モデルオブジェクトの種類
     * @param location 対象の位置
     * @return 入力用のオブジェクト
     * @throws IOException 入力の取得に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public <T extends Writable> ModelInput<T> openInput(
            Class<T> type,
            Location location) throws IOException {
        return hadoopDriver.openInput(type, location);
    }

    /**
     * 指定の名前を持つインポーター記述を返す。
     * @param info バッチの情報
     * @param name 出力の名前
     * @return インポーター記述
     */
    public Import getImporter(BatchInfo info, String name) {
        for (JobflowInfo jf : info.getJobflows()) {
            for (Import in : jf.getJobflow().getImports()) {
                if (in.getDescription().getName().equals(name)) {
                    return in;
                }
            }
        }
        throw new AssertionError(name);
    }

    /**
     * 指定の名前を持つエクスポーター記述を返す。
     * @param info バッチの情報
     * @param name 出力の名前
     * @return エクスポーター記述
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public Export getExporter(BatchInfo info, String name) {
        for (JobflowInfo jf : info.getJobflows()) {
            for (Export out : jf.getJobflow().getExports()) {
                if (out.getDescription().getName().equals(name)) {
                    return out;
                }
            }
        }
        throw new AssertionError(name);
    }

    /**
     * 指定の名前を持つインポーター記述を返す。
     * @param info ジョブフローの情報
     * @param name 出力の名前
     * @return インポーター記述
     */
    public Import getImporter(JobflowInfo info, String name) {
        for (Import in : info.getJobflow().getImports()) {
            if (in.getDescription().getName().equals(name)) {
                return in;
            }
        }
        throw new AssertionError(name);
    }

    /**
     * 指定の名前を持つエクスポーター記述を返す。
     * @param info ジョブフローの情報
     * @param name 出力の名前
     * @return エクスポーター記述
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public Export getExporter(JobflowInfo info, String name) {
        for (Export out : info.getJobflow().getExports()) {
            if (out.getDescription().getName().equals(name)) {
                return out;
            }
        }
        throw new AssertionError(name);
    }

    /**
     * 指定位置のファイルを開き、シーケンスファイルをモデルオブジェクトの列として読みだす。
     * @param <T> モデルオブジェクトの種類
     * @param type モデルオブジェクトの種類
     * @param location 対象の位置
     * @return 入力用のオブジェクト
     * @throws IOException 入力の取得に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public <T extends Writable> List<T> getList(
            Class<T> type,
            Location location) throws IOException {
        ModelInput<T> input = hadoopDriver.openInput(type, location);
        try {
            List<T> results = Lists.create();
            while (true) {
                T target = type.newInstance();
                if (input.readTo(target) == false) {
                    break;
                }
                results.add(target);
            }
            return results;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            input.close();
        }
    }

    /**
     * 指定位置のファイルを開き、シーケンスファイルをモデルオブジェクトの列として読みだす。
     * @param <T> モデルオブジェクトの種類
     * @param type モデルオブジェクトの種類
     * @param location 対象の位置
     * @param comparator 順序比較用のオブジェクト
     * @return 入力用のオブジェクト
     * @throws IOException 入力の取得に失敗した場合
     * @throws IllegalArgumentException 引数に{@code null}が指定された場合
     */
    public <T extends Writable> List<T> getList(
            Class<T> type,
            Location location,
            Comparator<? super T> comparator) throws IOException {
        List<T> list = getList(type, location);
        Collections.sort(list, comparator);
        return list;
    }

    private String path(String prefix, String name) {
        if (testName == null) {
            return prefix + "/" + name;
        } else {
            return testName + "/" + prefix + "/" + name;
        }
    }

    /**
     * フローへの入力を構築する。
     * @param <T> 入力するデータの種類
     */
    public class TestInput<T extends Writable> implements Closeable {

        private final Class<T> type;

        private final ModelOutput<T> output;

        private final String name;

        private final Location path;

        private final DataSize dataSize;

        TestInput(Class<T> type, String name, Location path, DataSize dataSize) throws IOException {
            assert type != null;
            assert name != null;
            assert path != null;
            this.type = type;
            this.name = name;
            this.path = path;
            this.output = hadoopDriver.openOutput(type, path);
            this.dataSize = dataSize;
        }

        /**
         * 指定のモデルを入力データとして追加する。
         * @param model 追加するモデルオブジェクト
         * @throws IOException 追加に失敗した場合
         */
        public void add(T model) throws IOException {
            output.write(model);
        }

        /**
         * この入力への編集を終了して、フローへの入力オブジェクトを生成する。
         * @return フローへの入力オブジェクト
         * @throws IOException 終了処理に失敗した場合
         * @throws IllegalArgumentException 引数に{@code null}が指定された場合
         */
        public In<T> flow() throws IOException {
            close();
            DirectImporterDescription description = new DirectImporterDescription(type, path.toPath('/'));
            description.setDataSize(dataSize);
            return flow.createIn(name, description);
        }

        @Override
        public void close() throws IOException {
            output.close();
        }
    }

    /**
     * フローからの出力を構築する。
     * @param <T> 出力するデータの種類
     */
    public class TestOutput<T extends Writable> {

        private final Class<T> type;

        private final String name;

        private final Location pathPrefix;

        TestOutput(Class<T> type, String name, Location pathPrefix) {
            assert type != null;
            assert name != null;
            assert pathPrefix != null;
            this.type = type;
            this.name = name;
            this.pathPrefix = pathPrefix;
        }

        /**
         * フローへの出力オブジェクトを生成する。
         * @return フローへの入力オブジェクト
         * @throws IOException 終了処理に失敗した場合
         * @throws IllegalArgumentException 引数に{@code null}が指定された場合
         */
        public Out<T> flow() throws IOException {
            return flow.createOut(name, new DirectExporterDescription(
                    type,
                    pathPrefix.getParent().append(pathPrefix.getName()).asPrefix().toPath('/')));
        }

        /**
         * 出力されたデータをリストに詰めて返す。
         * @return 出力されたデータの一覧
         * @throws IOException 出力されたデータの取得に失敗した場合
         * @throws IllegalArgumentException 引数に{@code null}が指定された場合
         */
        public List<T> toList() throws IOException {
            ModelInput<T> input = hadoopDriver.openInput(type, pathPrefix);
            try {
                List<T> results = Lists.create();
                while (true) {
                    T target = type.newInstance();
                    if (input.readTo(target) == false) {
                        break;
                    }
                    results.add(target);
                }
                return results;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            } finally {
                input.close();
            }
        }

        /**
         * 出力されたデータをリストに詰めて返す。
         * @param cmp データの順序
         * @return 出力されたデータの一覧
         * @throws IOException 出力されたデータの取得に失敗した場合
         * @throws IllegalArgumentException 引数に{@code null}が指定された場合
         */
        public List<T> toList(Comparator<? super T> cmp) throws IOException {
            List<T> results = toList();
            Collections.sort(results, cmp);
            return results;
        }
    }
}
