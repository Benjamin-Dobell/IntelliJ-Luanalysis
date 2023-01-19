// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.psi.LuaTypes.*;
import com.tang.intellij.lua.stubs.LuaExprPlaceStub;
import com.tang.intellij.lua.psi.*;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaCallExprImpl extends LuaExprMixin<LuaExprPlaceStub> implements LuaCallExpr {

  public LuaCallExprImpl(@NotNull LuaExprPlaceStub stub, @NotNull IStubElementType<?, ?> type) {
    super(stub, type);
  }

  public LuaCallExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaCallExprImpl(@NotNull LuaExprPlaceStub stub, @NotNull IElementType type, @NotNull ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitCallExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public LuaArgs getArgs() {
    return notNullChild(PsiTreeUtil.getStubChildOfType(this, LuaArgs.class));
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
  public ITy guessParentType(@NotNull SearchContext context) {
    return LuaPsiImplUtilKt.guessParentType(this, context);
  }

  @Override
  @Nullable
  public PsiElement getFirstStringArg() {
    return LuaPsiImplUtilKt.getFirstStringArg(this);
  }

  @Override
  public boolean isMethodDotCall() {
    return LuaPsiImplUtilKt.isMethodDotCall(this);
  }

  @Override
  public boolean isMethodColonCall() {
    return LuaPsiImplUtilKt.isMethodColonCall(this);
  }

  @Override
  public boolean isFunctionCall() {
    return LuaPsiImplUtilKt.isFunctionCall(this);
  }

  @Override
  @NotNull
  public LuaExpression<?> getExpression() {
    return LuaPsiImplUtilKt.getExpression(this);
  }

}
