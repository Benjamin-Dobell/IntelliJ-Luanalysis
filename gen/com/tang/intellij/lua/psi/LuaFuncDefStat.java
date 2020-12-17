// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.stubs.LuaFuncDefStatStub;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiReference;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;
import com.tang.intellij.lua.ty.ITyClass;

public interface LuaFuncDefStat extends LuaClassMethod<LuaFuncDefStatStub>, LuaDeclaration, LuaTypeScope, LuaStatement, StubBasedPsiElement<LuaFuncDefStatStub> {

  @Nullable
  LuaFuncBody getFuncBody();

  @Nullable
  PsiElement getId();

  @NotNull
  ItemPresentation getPresentation();

  @NotNull
  List<LuaParamDef> getParamDefList();

  @Nullable
  PsiElement getNameIdentifier();

  @NotNull
  PsiElement setName(@NotNull String name);

  @Nullable
  String getName();

  int getTextOffset();

  @Nullable
  ITy guessReturnType(@NotNull SearchContext searchContext);

  @NotNull
  ITyClass guessParentType(@NotNull SearchContext searchContext);

  @NotNull
  Visibility getVisibility();

  boolean isDeprecated();

  @NotNull
  LuaParamInfo[] getParams();

  @NotNull
  PsiReference[] getReferences();

}
