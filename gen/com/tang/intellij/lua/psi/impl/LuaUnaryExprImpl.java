// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.psi.LuaTypes.*;
import com.tang.intellij.lua.stubs.LuaUnaryExprStub;
import com.tang.intellij.lua.psi.*;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaUnaryExprImpl extends LuaExprMixin<LuaUnaryExprStub> implements LuaUnaryExpr {

  public LuaUnaryExprImpl(@NotNull LuaUnaryExprStub stub, @NotNull IStubElementType<?, ?> type) {
    super(stub, type);
  }

  public LuaUnaryExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaUnaryExprImpl(@NotNull LuaUnaryExprStub stub, @NotNull IElementType type, @NotNull ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitUnaryExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaCallExpr getCallExpr() {
    return PsiTreeUtil.getStubChildOfType(this, LuaCallExpr.class);
  }

  @Override
  @Nullable
  public LuaIndexExpr getIndexExpr() {
    return PsiTreeUtil.getStubChildOfType(this, LuaIndexExpr.class);
  }

  @Override
  @Nullable
  public LuaLiteralExpr getLiteralExpr() {
    return PsiTreeUtil.getStubChildOfType(this, LuaLiteralExpr.class);
  }

  @Override
  @Nullable
  public LuaNameExpr getNameExpr() {
    return PsiTreeUtil.getStubChildOfType(this, LuaNameExpr.class);
  }

  @Override
  @Nullable
  public LuaParenExpr getParenExpr() {
    return PsiTreeUtil.getStubChildOfType(this, LuaParenExpr.class);
  }

  @Override
  @Nullable
  public LuaTableExpr getTableExpr() {
    return PsiTreeUtil.getStubChildOfType(this, LuaTableExpr.class);
  }

  @Override
  @Nullable
  public LuaUnaryExpr getUnaryExpr() {
    return PsiTreeUtil.getStubChildOfType(this, LuaUnaryExpr.class);
  }

  @Override
  @NotNull
  public LuaUnaryOp getUnaryOp() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, LuaUnaryOp.class));
  }

  @Override
  @Nullable
  public LuaExpression<?> getExpression() {
    return LuaPsiImplUtilKt.getExpression(this);
  }

}
