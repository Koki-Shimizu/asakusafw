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
package test.inspector;


import org.apache.hadoop.io.Writable;

import com.asakusafw.testtools.inspect.AbstractInspector;

/**
 * 常に成功を返すテスト用のInspector
 */
public class SuccessInspector extends AbstractInspector {
    @Override
    protected void inspect(Writable expectRow, Writable actualRow) {
    }

}
