/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.ty.ITyClass
import com.tang.intellij.lua.ty.ITyGeneric

fun resolveLocal(context: SearchContext?, ref: LuaNameExpr) = resolveLocal(context, ref.name, ref)

fun resolveLocal(context: SearchContext?, refName: String, ref: PsiElement): PsiElement? {
    val element = resolveInFile(context, refName, ref)
    return if (element is LuaNameExpr) null else element
}

fun resolveInFile(context: SearchContext?, refName: String, pin: PsiElement): PsiElement? {
    var ret: PsiElement? = null
    LuaDeclarationTree.get(pin.containingFile).walkUp(pin) { decl ->
        if (decl.name == refName)
            ret = decl.firstDeclaration.psi
        ret == null
    }

    if (ret == null && refName == Constants.WORD_SELF) {
        val methodDef = PsiTreeUtil.getStubOrPsiParentOfType(pin, LuaClassMethodDefStat::class.java)
        if (methodDef != null && !methodDef.isStatic) {
            val methodName = methodDef.classMethodName
            val expr = methodName.expression
            ret = if (expr is LuaNameExpr && context != null && expr.name != Constants.WORD_SELF)
                resolve(context, expr)
            else
                expr
        }
    }
    return ret
}

fun isUpValue(context: SearchContext, ref: LuaNameExpr): Boolean {
    val funcBody = PsiTreeUtil.getParentOfType(ref, LuaFuncBody::class.java) ?: return false

    val refName = ref.name
    if (refName == Constants.WORD_SELF) {
        val classMethodFuncDefStat = PsiTreeUtil.getParentOfType(ref, LuaClassMethodDefStat::class.java)
        if (classMethodFuncDefStat != null && !classMethodFuncDefStat.isStatic) {
            val methodFuncBody = classMethodFuncDefStat.funcBody
            if (methodFuncBody != null)
                return methodFuncBody.textOffset < funcBody.textOffset
        }
    }

    val resolve = resolveLocal(context, ref)
    if (resolve != null) {
        if (!funcBody.textRange.contains(resolve.textRange))
            return true
    }

    return false
}

/**
 * 查找这个引用
 * @param nameExpr 要查找的ref
 * *
 * @param context context
 * *
 * @return PsiElement
 */
fun resolve(context: SearchContext, nameExpr: LuaNameExpr): PsiElement? {
    //search local
    var resolveResult = resolveInFile(context, nameExpr.name, nameExpr)

    //global
    if (resolveResult == null || resolveResult is LuaNameExpr) {
        val refName = nameExpr.name
        val moduleName = if (refName != Constants.WORD_MODULE) {
            nameExpr.getModuleName(context) ?: Constants.WORD_G
        } else Constants.WORD_G
        LuaClassMemberIndex.processNamespaceMember(context, moduleName, refName) {
            resolveResult = it
            false
        }
    }

    return resolveResult
}

fun multiResolve(context: SearchContext, ref: LuaNameExpr): Array<PsiElement> {
    val list = mutableListOf<PsiElement>()
    //search local
    val resolveResult = resolveInFile(context, ref.name, ref)
    if (resolveResult != null) {
        list.add(resolveResult)
    } else {
        val refName = ref.name
        val module = ref.getModuleName(context) ?: Constants.WORD_G
        LuaClassMemberIndex.processNamespaceMember(context, module, refName) {
            list.add(it)
            true
        }
        if (list.isEmpty() && refName == Constants.WORD_MODULE && module != Constants.WORD_G) {
            LuaClassMemberIndex.processNamespaceMember(context, Constants.WORD_G, refName) {
                list.add(it)
                true
            }
        }
    }
    return list.toTypedArray()
}

fun resolve(context: SearchContext, indexExpr: LuaIndexExpr): PsiElement? {
    val memberName = indexExpr.name

    if (memberName != null) {
        return resolve(context, indexExpr, memberName)
    }

    val idExpr = indexExpr.idExpr ?: return null

    val parentType = indexExpr.guessParentType(context)
    val indexTy = idExpr.guessType(context)

    if (indexTy == null) {
        return null
    }

    var memberPsi: PsiElement? = null

    parentType.eachTopClass { ty ->
        val cls = (if (ty is ITyGeneric) ty.base else ty) as? ITyClass
        memberPsi = cls?.findIndexer(context, indexTy)?.psi
        memberPsi == null
    }

    if (memberPsi == null) {
        val tree = LuaDeclarationTree.get(indexExpr.containingFile)
        val declaration = tree.find(indexExpr)
        if (declaration != null) {
            return declaration.psi
        }
    }

    return memberPsi
}

fun resolve(context: SearchContext, indexExpr: LuaIndexExpr, memberName: String): PsiElement? {
    val type = indexExpr.guessParentType(context)
    var ret: PsiElement? = null

    type.eachTopClass { ty ->
        val cls = (if (ty is ITyGeneric) ty.base else ty) as? ITyClass
        ret = cls?.findMember(context, memberName)?.psi
        ret == null
    }

    if (ret == null) {
        val tree = LuaDeclarationTree.get(indexExpr.containingFile)
        val declaration = tree.find(indexExpr)

        if (declaration != null) {
            return declaration.psi
        }
    }

    return ret
}

/**
 * 找到 require 的文件路径
 * @param pathString 参数字符串 require "aa.bb.cc"
 * *
 * @param project MyProject
 * *
 * @return PsiFile
 */
fun resolveRequireFile(pathString: String?, project: Project): LuaPsiFile? {
    if (pathString == null)
        return null
    val fileName = pathString.replace('.', '/')
    val f = LuaFileUtil.findFile(project, fileName)
    if (f != null) {
        val psiFile = PsiManager.getInstance(project).findFile(f)
        if (psiFile is LuaPsiFile)
            return psiFile
    }
    return null
}
