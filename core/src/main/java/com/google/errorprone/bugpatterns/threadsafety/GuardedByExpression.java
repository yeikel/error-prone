/*
 * Copyright 2014 Google Inc. All Rights Reserved.
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

package com.google.errorprone.bugpatterns.threadsafety;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;

/**
 * The lock expression of an {@code @GuardedBy} annotation.
 *
 * @author cushon@google.com (Liam Miller-Cushon)
 */
public abstract class GuardedByExpression {

  /**
   * A qualified this expression: [ClassName.]this
   */
  public static class QualifiedThis extends BaseNode {
    QualifiedThis(Symbol owner) {
      super(Kind.QUALIFIED_THIS, owner);
    }
  }

  /**
   * A 'class' literal: ClassName.class
   */
  public static class ClassLiteral extends BaseNode {
    ClassLiteral(Symbol owner) {
      super(Kind.CLASS_LITERAL, owner);
    }
  }

  /**
   * The base expression for a static member select on a class literal (e.g. ClassName.fieldName).
   */
  public static class TypeLiteral extends BaseNode {
    TypeLiteral(Symbol owner) {
      super(Kind.TYPE_LITERAL, owner);
    }
  }

  /**
   * A local variable (or parameter), resolved as part of a lock access expression.
   */
  public static class LocalVariable extends BaseNode {
    LocalVariable(Symbol.VarSymbol varSymbol) {
      super(Kind.LOCAL_VARIABLE, varSymbol);
    }
  }

  /**
   * A simple 'this literal.
   */
  public static class ThisLiteral extends GuardedByExpression {

    static final ThisLiteral INSTANCE = new ThisLiteral();

    @Override
    public Kind kind() {
      return Kind.THIS;
    }

    @Override
    public Symbol sym() {
      return null;
    }

    @Override
    public Type type() {
      return null;
    }

    private ThisLiteral() {}
  }

  /**
   * The member access expression for a field or method.
   */
  public static class Select extends GuardedByExpression {
    @Override
    public Kind kind() {
      return Kind.SELECT;
    }

    @Override
    public Symbol sym() {
      return sym;
    }

    @Override
    public Type type() {
      return type;
    }

    final GuardedByExpression base;
    private final Symbol sym;
    private final Type type;

    Select(GuardedByExpression base, Symbol sym, Type type) {
      this.base = base;
      this.sym = sym;
      this.type = type;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Select select = (Select) o;

      if (!base.equals(select.base)) {
        return false;
      }
      if (!sym.equals(select.sym)) {
        return false;
      }
      if (!type.equals(select.type)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = base.hashCode();
      result = 31 * result + sym.hashCode();
      result = 31 * result + type.hashCode();
      return result;
    }
  }

  /** Makes {@link GuardedByExpression}s. */
  public static class Factory {
    ThisLiteral thisliteral() {
      return ThisLiteral.INSTANCE;
    }

    QualifiedThis qualifiedThis(Symbol owner) {
      return new QualifiedThis(owner);
    }

    ClassLiteral classLiteral(Symbol clazz) {
      return new ClassLiteral(clazz);
    }

    TypeLiteral typeLiteral(Symbol type) {
      return new TypeLiteral(type);
    }

    Select select(GuardedByExpression base, Symbol member) {
      if (member instanceof VarSymbol) {
        return select(base, (VarSymbol) member);
      }
      if (member instanceof MethodSymbol) {
        return select(base, (MethodSymbol) member);
      }
      throw new IllegalStateException(
          "Bad select expression: expected symbol " + member.getKind());
    }

    Select select(GuardedByExpression base, Symbol.VarSymbol member) {
      return normalizedSelect(base, member, member.type);
    }

    Select select(GuardedByExpression base, Symbol.MethodSymbol member) {
      return normalizedSelect(base, member, member.getReturnType());
    }

    GuardedByExpression select(GuardedByExpression base, Select select) {
      return normalizedSelect(base, select.sym, select.type);
    }

    /** Normalize static accesses so they are not performed on instances. */
    private Select normalizedSelect(GuardedByExpression base, Symbol member, Type type) {
      if (member.isStatic()) {
        return new Select(typeLiteral(member.owner), member, type);
      }
      return new Select(base, member, type);
    }

    LocalVariable localVariable(Symbol.VarSymbol varSymbol) {
      return new LocalVariable(varSymbol);
    }
  }

  public abstract Kind kind();

  public abstract Symbol sym();

  public abstract Type type();

  /** {@link GuardedByExpression} kind. */
  public static enum Kind {
    THIS, CLASS_LITERAL, TYPE_LITERAL, LOCAL_VARIABLE, SELECT, QUALIFIED_THIS;
  }

  private abstract static class BaseNode extends GuardedByExpression {

    private final Kind kind;
    private final Symbol symbol;
    private final Type type;

    BaseNode(Kind kind, Symbol symbol) {
      this(kind, symbol, symbol.type);
    }

    BaseNode(Kind kind, Symbol symbol, Type type) {
      this.kind = kind;
      this.symbol = symbol;
      this.type = type;
    }

    @Override
    public Kind kind() {
      return kind;
    }

    @Override
    public Symbol sym() {
      return symbol;
    }

    @Override
    public Type type() {
      return type;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      BaseNode that = (BaseNode) o;

      if (kind != that.kind) {
        return false;
      }
      if (!symbol.equals(that.symbol)) {
        return false;
      }
      if (!type.equals(that.type)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = kind.hashCode();
      result = 31 * result + symbol.hashCode();
      result = 31 * result + type.hashCode();
      return result;
    }
  }

  @Override
  public String toString() {
    return PrettyPrinter.print(this);
  }

  public String debugPrint() {
    return DebugPrinter.print(this);
  }

  /**
   * Pretty printer for lock expressions.
   */
  private static class PrettyPrinter {
    public static String print(GuardedByExpression exp) {
      StringBuilder sb = new StringBuilder();
      pprint(exp, sb);
      return sb.toString();
    }

    private static void pprint(GuardedByExpression exp, StringBuilder sb) {
      switch (exp.kind()) {
        case CLASS_LITERAL:
          sb.append(String.format("%s.class", exp.sym().name));
          break;
        case THIS:
          sb.append("this");
          break;
        case QUALIFIED_THIS:
          sb.append(String.format("%s.this", exp.sym().name));
          break;
        case TYPE_LITERAL:
        case LOCAL_VARIABLE:
          sb.append(exp.sym().name);
          break;
        case SELECT:
          pprintSelect((Select) exp, sb);
          break;
      }
    }

    private static void pprintSelect(Select exp, StringBuilder sb) {
      pprint(exp.base, sb);
      sb.append(String.format(".%s", exp.sym().name));
    }
  }

  /**
   * s-exp pretty printer for lock expressions.
   */
  private static class DebugPrinter {
    public static String print(GuardedByExpression exp) {
      StringBuilder sb = new StringBuilder();
      pprint(exp, sb);
      return sb.toString();
    }

    private static void pprint(GuardedByExpression exp, StringBuilder sb) {
      switch (exp.kind()) {
        case TYPE_LITERAL:
        case CLASS_LITERAL:
        case QUALIFIED_THIS:
        case LOCAL_VARIABLE:
          sb.append(String.format("(%s %s)", exp.kind(), exp.sym()));
          break;
        case THIS:
          sb.append("(THIS)");
          break;
        case SELECT:
          pprintSelect((Select) exp, sb);
          break;
      }
    }

    private static void pprintSelect(Select exp, StringBuilder sb) {
      sb.append(String.format("(%s ", exp.kind()));
      pprint(exp.base, sb);
      sb.append(String.format(" %s)", exp.sym));
    }
  }
}