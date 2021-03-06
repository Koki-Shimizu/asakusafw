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
package com.asakusafw.utils.java.model.syntax;


/**
 * 注釈要素を構成する名前と値のペアを表現するインターフェース。
 * <ul>
 *   <li> Specified In: <ul>
 *     <li> {@code [JLS3:9.7] Annotations (<i>ElementValuePair</i>)} </li>
 *   </ul> </li>
 * </ul>
 */
public interface AnnotationElement
        extends Invocation {

    // properties

    /**
     * 注釈要素の名前を返す。
     * @return
     *     注釈要素の名前
     */
    SimpleName getName();

    /**
     * 注釈要素値の式を返す。
     * @return
     *     注釈要素値の式
     */
    Expression getExpression();
}
