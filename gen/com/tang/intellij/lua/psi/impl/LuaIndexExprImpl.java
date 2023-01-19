// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.psi.LuaTypes.*;
import com.tang.intellij.lua.psi.*;
import com.intellij.navigation.ItemPresentation;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;
import com.tang.intellij.lua.stubs.LuaIndexExprStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaIndexExprImpl extends LuaIndexExprMixin implements LuaIndexExpr {

  public LuaIndexExprImpl(@NotNull LuaIndexExprStub stub, @NotNull IStubElementType<?, ?> type) {
    super(stub, type);
  }

  public LuaIndexExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaIndexExprImpl(@NotNull LuaIndexExprStub stub, @NotNull IElementType type, @NotNull ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitIndexExpr(this);
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
  public PsiElement getId() {
    return findChildByType(ID);
  }

  @Override
  @Nullable
  public PsiElement getNameIdentifier() {
    return LuaPsiImplUtilKt.getNameIdentifier(this);
  }

  @Override
  @NotNull
  public PsiElement setName(@NotNull String name) {
    return LuaPsiImplUtilKt.setName(this, name);
  }

  @Override
  @Nullable
  public String getName() {
    return LuaPsiImplUtilKt.getName(this);
  }

  @Override
  public int getTextOffset() {
    return LuaPsiImplUtilKt.getTextOffset(this);
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return LuaPsiImplUtilKt.getPresentation(this);
  }

  @Override
  @Nullable
  public LuaExpression<?> getIdExpr() {
    return LuaPsiImplUtilKt.getIdExpr(this);
  }

  @Override
  @NotNull
  public String toString() {
    return LuaPsiImplUtilKt.toString(this);
  }

  @Override
  @Nullable
  public ITy guessIndexType(@NotNull SearchContext context) {
    return LuaPsiImplUtilKt.guessIndexType(this, context);
  }

  @Override
  @NotNull
  public ITy guessParentType(@NotNull SearchContext context) {
    return LuaPsiImplUtilKt.guessParentType(this, context);
  }

  @Override
  public boolean isDeprecated() {
    return LuaPsiImplUtilKt.isDeprecated(this);
  }

  @Override
  public boolean isExplicitlyTyped() {
    return LuaPsiImplUtilKt.isExplicitlyTyped(this);
  }

  @Override
  @NotNull
  public List<LuaExpression<?>> getExpressionList() {
    return LuaPsiImplUtilKt.getExpressionList(this);
  }

  @Override
  @Nullable
  public PsiElement getDot() {
    return findChildByType(DOT);
  }

  @Override
  @Nullable
  public PsiElement getColon() {
    return findChildByType(COLON);
  }

  @Override
  @Nullable
  public PsiElement getLbrack() {
    return findChildByType(LBRACK);
  }

}
