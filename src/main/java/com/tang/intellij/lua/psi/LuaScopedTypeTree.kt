/*
 * Copyright (c) 2020
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

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ty.*
import java.util.*

open class FoundLuaScope(open val scope: LuaScopedTypeTreeScope, val psiScopedTypeIndex: Int? = null)

interface LuaScopedTypeTreeScope {
    val name: String
    val psi: PsiElement
    val tree: LuaScopedTypeTree
    val parent: LuaScopedTypeTreeScope?

    fun findName(context: SearchContext, name: String, beforeIndex: Int? = null): LuaScopedType?
    fun findOwner(context: SearchContext): ITy?
}

interface LuaScopedTypeTree {
    companion object {
        private val treeKey = Key.create<ScopedTypeTree>("lua.object.tree.types")

        fun get(file: PsiFile): LuaScopedTypeTree? {
            if (file !is LuaPsiFile) {
                return null
            }

            val currentTree = file.getUserData(treeKey)

            if (currentTree?.shouldRebuild() != false) {
                if (!file.isContentsLoaded) {
                    try {
                        return ScopedTypeStubTree(file).apply {
                            buildTree(file)
                            file.putUserData(treeKey, this)
                        }
                    } catch (e: Exception) {
                        // Fallback to PSI tree
                    }
                }

                return ScopedTypePsiTree(file).apply {
                    buildTree(file)
                    file.putUserData(treeKey, this)
                }
            }

            return currentTree
        }
    }

    fun findName(context: SearchContext, pin: PsiElement, name: String): LuaScopedType?
    fun findOwner(context: SearchContext, pin: PsiElement): ITy?
    fun findScope(element: PsiElement): FoundLuaScope?
}

private class ScopedTypeTreeScope(override val psi: LuaTypeScope, override val tree: ScopedTypeTree, override val parent: ScopedTypeTreeScope?): LuaScopedTypeTreeScope {
    override val name = psi.containingFile.getFileIdentifier() + "@" + psi.node.startOffset

    private val types = ArrayList<LuaScopedType>(0)
    private val childScopes = LinkedList<ScopedTypeTreeScope>()

    private var dumbCachedOwner: ITy? = null
    private var isDumbOwnerCached = false

    private var cachedOwner: ITy? = null
    private var isOwnerCached = false

    fun addChildScope(scope: ScopedTypeTreeScope) {
        childScopes.add(scope)
    }

    fun add(type: LuaScopedType) {
        types.add(type)
    }

    fun addAll(type: Collection<LuaScopedType>) {
        types.addAll(type)
    }

    inline fun forEach(action: (LuaScopedType) -> Unit) {
        types.forEach(action)
    }

    inline fun forEachIndexed(action: (index: Int, LuaScopedType) -> Unit) {
        types.forEachIndexed(action)
    }

    fun indexOf(type: LuaScopedType): Int? {
        val index = types.indexOf(type)
        return if (index >= 0) index else null
    }

    fun get(name: String, beforeIndex: Int): LuaScopedType? {
        for (i in beforeIndex - 1 downTo 0) {
            val type = types[i]

            if (type.name == name) {
                return type
            }
        }

        return null
    }

    fun get(name: String): LuaScopedType? {
        types.forEach {
            if (it.name == name) {
                return it
            }
        }

        return null
    }

    override fun findName(context: SearchContext, name: String, beforeIndex: Int?): LuaScopedType? {
        val type = if (beforeIndex != null) {
            get(name, beforeIndex)
        } else {
            get(name)
        }

        if (type != null) {
            return type
        }

        val cls: ITy? = findOwner(context)

        if (cls?.isAnonymous == false) {
            val classTag = if (cls is TySerializedClass) {
                LuaClassIndex.find(context, cls.className)
            } else if (cls is TyPsiDocClass) {
                cls.tagClass
            } else null

            // Need to ensure we don't check the same scope *without* beforeIndex
            if (classTag != psi) {
                val genericDef = PsiTreeUtil.getStubChildrenOfTypeAsList(classTag, LuaDocGenericDef::class.java).firstOrNull {
                    it.name == name
                }

                if (genericDef != null) {
                    return genericDef
                }
            }
        }

        return parent?.findName(context, name)
    }

    private fun guessOwner(context: SearchContext): ITy? {
        return when (psi) {
            is LuaDocTagClass -> psi.type
            is LuaClassMethodDefStat -> psi.guessParentType(context)
            is LuaClosureExpr -> psi.guessParentType(context)
            else -> parent?.findOwner(context)
        }
    }

    private fun findDumbOwner(context: SearchContext): ITy? {
        if (isOwnerCached) {
            return cachedOwner
        }

        if (isDumbOwnerCached) {
            return dumbCachedOwner
        }

        val owner = guessOwner(context)

        if (owner != null) {
            dumbCachedOwner = owner
            isDumbOwnerCached = true
        }

        return owner
    }

    private fun findSmartOwner(context: SearchContext): ITy? {
        if (isOwnerCached) {
            return cachedOwner
        }

        val owner = guessOwner(context)

        if (owner != null) {
            cachedOwner = owner
            isOwnerCached = true
        }

        return owner
    }

    override fun findOwner(context: SearchContext): ITy? {
        return if (context.isDumb) {
            findDumbOwner(context)
        } else {
            findSmartOwner(context)
        }
    }
}

private class FoundScope(override val scope: ScopedTypeTreeScope, psiScopedTypeIndex: Int? = null): FoundLuaScope(scope, psiScopedTypeIndex)

private abstract class ScopedTypeTree(val file: LuaPsiFile) : LuaRecursiveVisitor(), LuaScopedTypeTree {
    companion object {
        val scopeKey = Key.create<ScopedTypeTreeScope>("lua.object.tree.types.scope")
    }

    private val modificationStamp: Long = file.modificationStamp

    private val rootScope = ScopedTypeTreeScope(file, this, null)
    private var currentScope = rootScope

    open fun shouldRebuild(): Boolean {
        return modificationStamp != file.modificationStamp
    }

    private fun create(psi: LuaTypeScope): ScopedTypeTreeScope {
        val genericDefs = if (psi is LuaDocFunctionTy) {
            psi.genericDefList
        } else if (psi is LuaDocTagAlias) {
            psi.genericDefList
        } else {
            val comment = if (psi is LuaCommentOwner) {
                LuaCommentUtil.findComment(psi)
            } else if (psi is LuaDocPsiElement) {
                LuaCommentUtil.findContainer(psi)
            } else null

            comment?.findGenericDefs()
        }

        val scope = ScopedTypeTreeScope(psi, this, currentScope)

        genericDefs?.let {
            scope.addAll(it)
        }

        return scope
    }

    private fun push(scope: ScopedTypeTreeScope) {
        currentScope.addChildScope(scope)
        currentScope = scope
    }

    private fun pop() {
        currentScope.psi.putUserData(scopeKey, currentScope)
        currentScope = currentScope.parent ?: rootScope
    }

    fun buildTree(file: PsiFile) {
        file.accept(this)
    }

    abstract override fun findScope(element: PsiElement): FoundScope?

    override fun findOwner(context: SearchContext, pin: PsiElement): ITy? {
        val scopeSourceElement = if (pin is LuaDocPsiElement) {
            val comment = LuaCommentUtil.findContainer(pin)
            val cls = comment.tagClass

            if (cls != null) {
                return cls.type
            }

            comment.owner ?: pin
        } else {
            pin
        }

        return findScope(scopeSourceElement)?.scope?.findOwner(context)
    }

    override fun findName(context: SearchContext, pin: PsiElement, name: String): LuaScopedType? {
        return findScope(pin)?.let {
            it.scope.findName(context, name, it.psiScopedTypeIndex)
        }
    }

    protected open fun traverseChildren(element: PsiElement) {
        super.visitElement(element)
    }

    override fun visitElement(element: PsiElement) {
        // WARNING: (element !is LuaDocTagClass || currentScope.psi !is LuaDocTagClass) is used instead of (element != currentScope.psi)
        //          because IDEA gives us back PSI representing the same content that are *not* equal.
        if (element is LuaTypeScope && (element !is LuaDocTagClass || currentScope.psi !is LuaDocTagClass)) {
            push(create(element))
            traverseChildren(element)
            pop()
        } else if (element is LuaDocTagOverload && currentScope.psi !is LuaDocTagClass) {
            // Typically generic defs (@generic) are scoped to the function owner. However, overloads are a special case
            // where generics defs must be scoped to the overload only i.e. cannot be referenced in the function body.
            val previousScope = currentScope
            currentScope = currentScope.parent ?: rootScope

            traverseChildren(element)

            currentScope = previousScope
        } else {
            val cls = (element as? LuaComment)?.tagClass

            if (cls != null) {
                // If we encountered a comment with a @class then we want all comment children (i.e. @field) to be children of the class' scope.
                push(create(cls))
                traverseChildren(element)
                pop()
            } else {
                traverseChildren(element)
            }
        }
    }
}

private class ScopedTypePsiTree(file: LuaPsiFile) : ScopedTypeTree(file) {
    override fun findScope(element: PsiElement): FoundScope? {
        var psi: PsiElement? = element
        var psiScopedType: LuaScopedType? = null

        while (psi != null) {
            if (psiScopedType == null && psi is LuaScopedType) {
                psiScopedType = psi
            }

            val candidatePsi = if (psi is LuaComment) {
                psi.tagClass ?: psi.owner ?: psi
            } else {
                psi
            }

            val scope = (candidatePsi as? LuaTypeScope)?.let {
                it.getUserData(scopeKey) ?: run {
                    buildTree(element.containingFile)
                    candidatePsi.getUserData(scopeKey)
                }
            }

            if (scope != null) {
                return FoundScope(scope, psiScopedType?.let { scope.indexOf(psiScopedType) })
            }

            psi = psi.parent
        }

        return null
    }
}

private class ScopedTypeStubTree(file: LuaPsiFile) : ScopedTypeTree(file) {
    override fun shouldRebuild(): Boolean {
        return super.shouldRebuild() || (file as? LuaPsiFile)?.isContentsLoaded == true
    }

    override fun traverseChildren(element: PsiElement) {
        var stub: STUB_ELE? = null

        if (element is LuaPsiFile) {
            stub = element.stub
        }

        if (element is STUB_PSI) {
            stub = element.stub
        }

        if (stub != null) {
            for (child in stub.childrenStubs) {
                child.psi.accept(this)
            }
        } else {
            super.traverseChildren(element)
        }
    }

    override fun findScope(element: PsiElement): FoundScope? {
        if (element is STUB_PSI) {
            var stub = element.stub
            var psiScopedType: LuaScopedType? = null

            while (stub != null) {
                val stubPsi = stub.psi

                if (psiScopedType == null && stubPsi is LuaScopedType) {
                    psiScopedType = stubPsi
                }

                val scope = (stubPsi as? LuaTypeScope)?.let {
                    it.getUserData(scopeKey) ?: run {
                        buildTree(element.containingFile)
                        stubPsi.getUserData(scopeKey)
                    }
                }

                if (scope != null) {
                    return FoundScope(scope, psiScopedType?.let { scope.indexOf(psiScopedType) })
                }


                stub = stub.parentStub
            }
        }

        return null
    }
}

class ScopedTypeSubstitutor(context: SearchContext, val scope: LuaScopedTypeTreeScope) : TySubstitutor() {
    override val name = "scoped:" + scope.name

    override fun substitute(context: SearchContext, clazz: ITyClass): ITy {
        return (clazz as? TyGenericParameter)?.let { genericParam ->
            val scopedTy = scope.findName(context, genericParam.varName)?.type as? TyGenericParameter

            if (scopedTy?.className == genericParam.className) {
                // If the generic parameter we found is the same as the source, then we're within the scope in which the generic parameter is defined.
                // In this scope, the generic parameter is a concrete (albeit unknown) type.
                TyClass.createConcreteGenericParameter(genericParam)
            } else {
                genericParam
            }

        } ?: clazz
    }

    companion object {
        fun substitute(context: SearchContext, ty: ITy): ITy {
            context.element?.let { contextElement ->
                LuaScopedTypeTree.get(contextElement.containingFile)?.findScope(contextElement)?.scope?.let { scope ->
                    return ty.substitute(context, ScopedTypeSubstitutor(context, scope))
                }
            }

            return ty
        }
    }
}
