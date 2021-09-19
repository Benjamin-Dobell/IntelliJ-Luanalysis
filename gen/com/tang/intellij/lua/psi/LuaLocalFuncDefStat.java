// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.stubs.LuaLocalFuncDefStub;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.search.SearchScope;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaLocalFuncDefStat extends LuaFuncBodyOwner<LuaLocalFuncDefStub>, LuaDeclaration, LuaTypeScope, PsiNameIdentifierOwner, LuaStatement, StubBasedPsiElement<LuaLocalFuncDefStub> {

  @Nullable
  LuaFuncBody getFuncBody();

  @Nullable
  PsiElement getId();

  @NotNull
  List<LuaParamDef> getParamDefList();

  @Nullable
  PsiElement getNameIdentifier();

  @NotNull
  PsiElement setName(@NotNull String name);

  @Nullable
  String getName();

  int getTextOffset();

  @NotNull
  SearchScope getUseScope();

  @Nullable
  ITy guessReturnType(@NotNull SearchContext context);

  @NotNull
  ITy guessParentType(@NotNull SearchContext context);

  @NotNull
  LuaParamInfo[] getParams();

}
