// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.tang.intellij.lua.psi.LuaClass;
import com.tang.intellij.lua.psi.LuaScopedType;
import com.tang.intellij.lua.ty.TyGenericParameter;

public interface LuaDocGenericDef extends PsiNameIdentifierOwner, LuaDocPsiElement, LuaClass, LuaScopedType {

  @Nullable
  LuaDocClassRef getClassRef();

  @NotNull
  PsiElement getId();

  @NotNull
  TyGenericParameter getType();

  @Nullable
  PsiElement getNameIdentifier();

  @NotNull
  PsiElement setName(@NotNull String newName);

  @Nullable
  String getName();

  int getTextOffset();

}
