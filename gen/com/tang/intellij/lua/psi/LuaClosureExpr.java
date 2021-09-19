// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.stubs.LuaClosureExprStub;
import com.intellij.psi.StubBasedPsiElement;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaClosureExpr extends LuaFuncBodyOwner<LuaClosureExprStub>, LuaExpression<LuaClosureExprStub>, LuaTypeScope, StubBasedPsiElement<LuaClosureExprStub> {

  @NotNull
  LuaFuncBody getFuncBody();

  @NotNull
  List<LuaParamDef> getParamDefList();

  @Nullable
  ITy guessReturnType(@NotNull SearchContext context);

  @NotNull
  ITy guessParentType(@NotNull SearchContext context);

  @NotNull
  LuaParamInfo[] getParams();

}
