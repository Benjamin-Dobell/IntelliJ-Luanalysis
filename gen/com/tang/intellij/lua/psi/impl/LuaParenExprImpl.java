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
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaParenExprImpl extends LuaExprMixin<LuaExprPlaceStub> implements LuaParenExpr {

  public LuaParenExprImpl(@NotNull LuaExprPlaceStub stub, @NotNull IStubElementType<?, ?> type) {
    super(stub, type);
  }

  public LuaParenExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaParenExprImpl(@NotNull LuaExprPlaceStub stub, @NotNull IElementType type, @NotNull ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitParenExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaExpression<?> getExpression() {
    return LuaPsiImplUtilKt.getExpression(this);
  }

}
