// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.stubs.LuaClassMethodDefStatStub;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.navigation.ItemPresentation;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaClassMethodDefStat extends LuaTypeMethod<LuaClassMethodDefStatStub>, LuaDeclaration, LuaTypeScope, LuaStatement, StubBasedPsiElement<LuaClassMethodDefStatStub> {

  @NotNull
  LuaClassMethodName getClassMethodName();

  @Nullable
  LuaFuncBody getFuncBody();

  @NotNull
  ITy guessParentType(@NotNull SearchContext context);

  @NotNull
  Visibility getVisibility();

  boolean isDeprecated();

  boolean isExplicitlyTyped();

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
  LuaParamInfo[] getParams();

  boolean isStatic();

  @NotNull
  ItemPresentation getPresentation();

}
