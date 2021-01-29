// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.psi.LuaTypes.*;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.tang.intellij.lua.stubs.LuaReturnStatStub;
import com.tang.intellij.lua.psi.*;
import com.tang.intellij.lua.ty.ITy;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaReturnStatImpl extends StubBasedPsiElementBase<LuaReturnStatStub> implements LuaReturnStat {

  public LuaReturnStatImpl(@NotNull LuaReturnStatStub stub, @NotNull IStubElementType type) {
    super(stub, type);
  }

  public LuaReturnStatImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaReturnStatImpl(LuaReturnStatStub stub, IElementType type, ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitReturnStat(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaExprList getExprList() {
    return PsiTreeUtil.getStubChildOfType(this, LuaExprList.class);
  }

  @Override
  @Nullable
  public ITy getType() {
    return LuaPsiImplUtilKt.getType(this);
  }

}
