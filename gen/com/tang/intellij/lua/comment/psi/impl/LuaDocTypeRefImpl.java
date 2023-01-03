// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.comment.psi.LuaDocTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.tang.intellij.lua.comment.psi.*;
import com.intellij.psi.PsiReference;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public class LuaDocTypeRefImpl extends ASTWrapperPsiElement implements LuaDocTypeRef {

  public LuaDocTypeRefImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaDocVisitor visitor) {
    visitor.visitTypeRef(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaDocVisitor) accept((LuaDocVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getId() {
    return findChildByType(ID);
  }

  @Override
  @NotNull
  public String getName() {
    return LuaDocPsiImplUtilKt.getName(this);
  }

  @Override
  @NotNull
  public PsiReference getReference() {
    return LuaDocPsiImplUtilKt.getReference(this);
  }

  @Override
  @NotNull
  public ITy resolveType(@NotNull SearchContext context) {
    return LuaDocPsiImplUtilKt.resolveType(this, context);
  }

}
