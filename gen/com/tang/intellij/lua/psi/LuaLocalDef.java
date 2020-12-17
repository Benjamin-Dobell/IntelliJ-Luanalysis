// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.tang.intellij.lua.stubs.LuaLocalDefStub;
import com.intellij.psi.search.SearchScope;

public interface LuaLocalDef extends LuaNamedElement, LuaTypeGuessable, PsiNameIdentifierOwner, StubBasedPsiElement<LuaLocalDefStub> {

  @NotNull
  PsiElement getId();

  @NotNull
  String getName();

  @NotNull
  PsiElement setName(@NotNull String name);

  @NotNull
  PsiElement getNameIdentifier();

  @NotNull
  SearchScope getUseScope();

  @Nullable
  LuaCloseAttribute getClose();

  @Nullable
  LuaConstAttribute getConst();

}
