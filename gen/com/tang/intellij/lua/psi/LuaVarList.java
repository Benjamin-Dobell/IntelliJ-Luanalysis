// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.tang.intellij.lua.stubs.LuaPlaceholderStub;

public interface LuaVarList extends LuaExprList, StubBasedPsiElement<LuaPlaceholderStub> {

  @NotNull
  List<LuaCallExpr> getCallExprList();

  @NotNull
  List<LuaIndexExpr> getIndexExprList();

  @NotNull
  List<LuaLiteralExpr> getLiteralExprList();

  @NotNull
  List<LuaNameExpr> getNameExprList();

  @NotNull
  List<LuaParenExpr> getParenExprList();

  @NotNull
  List<LuaTableExpr> getTableExprList();

}
