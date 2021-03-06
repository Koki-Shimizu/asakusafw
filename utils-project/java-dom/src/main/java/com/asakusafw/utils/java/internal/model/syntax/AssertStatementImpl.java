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
package com.asakusafw.utils.java.internal.model.syntax;

import com.asakusafw.utils.java.model.syntax.AssertStatement;
import com.asakusafw.utils.java.model.syntax.Expression;
import com.asakusafw.utils.java.model.syntax.ModelKind;
import com.asakusafw.utils.java.model.syntax.Visitor;

/**
 * {@link AssertStatement}の実装。
 */
public final class AssertStatementImpl extends ModelRoot implements AssertStatement {

    /**
     * 表明式。
     */
    private Expression expression;

    /**
     * メッセージ式。
     */
    private Expression message;

    @Override
    public Expression getExpression() {
        return this.expression;
    }

    /**
     * 表明式を設定する。
     * @param expression
     *     表明式
     * @throws IllegalArgumentException
     *     {@code expression}に{@code null}が指定された場合
     */
    public void setExpression(Expression expression) {
        Util.notNull(expression, "expression"); //$NON-NLS-1$
        this.expression = expression;
    }

    @Override
    public Expression getMessage() {
        return this.message;
    }

    /**
     * メッセージ式を設定する。
     * <p> メッセージ式が省略された場合、引数には{@code null}を指定する。 </p>
     * @param message
     *     メッセージ式、
     *     ただしメッセージ式が省略された場合は{@code null}
     */
    public void setMessage(Expression message) {
        this.message = message;
    }

    /**
     * この要素の種類を表す{@link ModelKind#ASSERT_STATEMENT}を返す。
     * @return {@link ModelKind#ASSERT_STATEMENT}
     */
    @Override
    public ModelKind getModelKind() {
        return ModelKind.ASSERT_STATEMENT;
    }

    @Override
    public <R, C, E extends Throwable> R accept(
            Visitor<R, C, E> visitor, C context) throws E {
        Util.notNull(visitor, "visitor"); //$NON-NLS-1$
        return visitor.visitAssertStatement(this, context);
    }
}
