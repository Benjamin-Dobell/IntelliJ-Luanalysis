// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.comment.psi.LuaDocTypes.*;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.tang.intellij.lua.stubs.LuaDocTagNotStub;
import com.tang.intellij.lua.comment.psi.*;
import com.tang.intellij.lua.ty.ITy;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaDocTagNotImpl extends StubBasedPsiElementBase<LuaDocTagNotStub> implements LuaDocTagNot {

  public LuaDocTagNotImpl(@NotNull LuaDocTagNotStub stub, @NotNull IStubElementType type) {
    super(stub, type);
  }

  public LuaDocTagNotImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaDocTagNotImpl(LuaDocTagNotStub stub, IElementType type, ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaDocVisitor visitor) {
    visitor.visitTagNot(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaDocVisitor) accept((LuaDocVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaDocTypeList getTypeList() {
    return PsiTreeUtil.getChildOfType(this, LuaDocTypeList.class);
  }

  @Override
  @NotNull
  public ITy getType(int index) {
    return LuaDocPsiImplUtilKt.getType(this, index);
  }

  @Override
  @NotNull
  public ITy getType() {
    return LuaDocPsiImplUtilKt.getType(this);
  }

  @Override
  @Nullable
  public PsiElement getVariadic() {
    return findChildByType(ELLIPSIS);
  }

}
