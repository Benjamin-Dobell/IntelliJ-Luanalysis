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

package com.tang.intellij.lua.ty

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.Processor
import com.tang.intellij.lua.comment.psi.LuaDocFunctionTy
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.*

interface IFunSignature {
    val colonCall: Boolean
    val returnTy: ITy?
    val params: Array<LuaParamInfo>?
    val displayName: String
    val paramSignature: String
    val tyParameters: Array<TyParameter>?
    val varargTy: ITy?

    fun substitute(substitutor: ITySubstitutor): IFunSignature
    fun equals(other: IFunSignature, context: SearchContext): Boolean
    fun contravariantOf(other: IFunSignature, context: SearchContext, flags: Int): Boolean
}

fun IFunSignature.processParameters(callExpr: LuaCallExpr, processor: (index:Int, param: LuaParamInfo) -> Boolean) {
    val expr = callExpr.expr
    val thisTy = if (expr is LuaIndexExpr) {
        expr.guessType(SearchContext.get(expr.project))
    } else null
    processParameters(thisTy, callExpr.isMethodColonCall, processor)
}

fun IFunSignature.processParameters(thisTy: ITy?, colonStyle: Boolean, processor: (index:Int, param: LuaParamInfo) -> Boolean) {
    var index = 0
    var pIndex = 0
    if (colonStyle && !colonCall) {
        pIndex++
    } else if (!colonStyle && colonCall) {
        val pi = LuaParamInfo.createSelf(thisTy)
        if (!processor(index++, pi)) return
    }

    params?.let {
        for (i in pIndex until it.size) {
            if (!processor(index++, it[i])) return
        }
    }
}

fun IFunSignature.processParameters(processor: (index:Int, param: LuaParamInfo) -> Boolean) {
    var index = 0
    if (colonCall)
        index++

    params?.let {
        for (i in it.indices) {
            if (!processor(index++, it[i])) return
        }
    }
}

fun IFunSignature.processParams(thisTy: ITy?, colonStyle: Boolean, processor: (index:Int, param: LuaParamInfo) -> Boolean) {
    var index = 0
    if (colonCall) {
        val pi = LuaParamInfo.createSelf(thisTy)
        if (!processor(index++, pi)) return
    }

    params?.let {
        for (element in it) {
            if (!processor(index++, element)) return
        }
    }
}

fun IFunSignature.getFirstParam(thisTy: ITy?, colonStyle: Boolean): LuaParamInfo? {
    var pi: LuaParamInfo? = null
    processParams(thisTy, colonStyle) { _, paramInfo ->
        pi = paramInfo
        false
    }
    return pi
}

fun IFunSignature.getParamTy(index: Int): ITy {
    val info = params?.getOrNull(index)
    return info?.ty ?: Ty.UNKNOWN
}

//eg. print(...)
fun IFunSignature.hasVarargs(): Boolean {
    return this.varargTy != null
}

fun IFunSignature.isGeneric() = tyParameters?.isNotEmpty() == true

abstract class FunSignatureBase(override val colonCall: Boolean,
                                override val params: Array<LuaParamInfo>?,
                                override val tyParameters: Array<TyParameter>? = null
) : IFunSignature {
    override fun equals(other: Any?): Boolean {
        if (other is IFunSignature) {
            return colonCall == other.colonCall
                    && params?.let { other.params?.contentEquals(it) ?: false } ?: (other.params == null)
                    && tyParameters?.let { other.tyParameters?.contentEquals(it) ?: false } ?: (other.tyParameters == null)
                    && returnTy == other.returnTy
                    && varargTy == other.varargTy
        }
        return false
    }

    override fun equals(other: IFunSignature, context: SearchContext): Boolean {
        if (colonCall != other.colonCall) {
            return false
        }

        val returnTyEqual = returnTy?.let {
            other.returnTy?.equals(it, context) ?: false
        } ?: (other.returnTy === null)

        if (!returnTyEqual) {
            return false
        }

        val varargTyEqual = varargTy?.let {
            other.varargTy?.equals(it, context) ?: false
        } ?: (other.varargTy === null)

        if (!varargTyEqual) {
            return false
        }

        val paramsEqual = params?.let { params ->
            other.params?.let { otherParams ->
                params.size == otherParams.size && otherParams.asSequence().zip(params.asSequence()).all { (param, otherParam) ->
                    param.equals(otherParam, context)
                }
            } ?: false
        } ?: (other.params == null)

        if (!paramsEqual) {
            return false
        }

        return tyParameters?.let { params ->
            other.tyParameters?.let { otherParams ->
                params.size == otherParams.size && otherParams.asSequence().zip(params.asSequence()).all { (param, otherParam) ->
                    param.equals(otherParam, context)
                }
            } ?: false
        } ?: (other.tyParameters == null)
    }

    override fun hashCode(): Int {
        var code = if (colonCall) 1 else 0
        params?.forEach {
            code = code * 31 + it.hashCode()
        }
        tyParameters?.forEach {
            code = code * 31 + it.hashCode()
        }
        code = code * 31 + (returnTy?.hashCode() ?: 0)
        code = code * 31 + (varargTy?.hashCode() ?: 0)
        return code
    }

    override val displayName: String by lazy {
        val paramsText = params?.map {
            val paramTy = it.ty
            val paramTypeName = if (paramTy is TyParameter && paramTy.superClass != null) {
                "(${paramTy.displayName})"
            } else paramTy.displayName
            it.name + ": " + paramTypeName
        }?.let { "(${it.joinToString(", ")})" } ?: ""

        val returnTypeName = returnTy?.let {
            if (it is TyParameter && it.superClass != null) {
                "(${it.displayName})"
            } else it.displayName
        }

        "fun${paramsText}${returnTypeName?.let {": " + it}}"
    }

    override val paramSignature: String get() {
        return params?.let {
            val list = arrayOfNulls<String>(it.size)
            for (i in it.indices) {
                val lpi = it[i]
                list[i] = lpi.name
            }
            return "(" + list.joinToString(", ") + ")"
        } ?: ""
    }

    override fun substitute(substitutor: ITySubstitutor): IFunSignature {
        var paramsSubstituted = false
        val substitutedParams = params?.map {
            val substitutedParam = it.substitute(substitutor)

            if (substitutedParam !== it) {
                paramsSubstituted = true
            }

            substitutedParam
        }

        val substitutedReturnTy = returnTy?.substitute(substitutor)
        val substitutedVarargTy = varargTy?.let { TyMultipleResults.getResult(it.substitute(substitutor)) }

        return if (paramsSubstituted || substitutedReturnTy !== returnTy || substitutedVarargTy !== varargTy) {
            FunSignature(colonCall,
                    substitutedReturnTy,
                    substitutedVarargTy,
                    substitutedParams?.toTypedArray(),
                    tyParameters)
        } else {
            this
        }
    }

    override fun contravariantOf(other: IFunSignature, context: SearchContext, flags: Int): Boolean {
        params?.let {
            val otherParams = other.params

            if (otherParams == null) {
                return false
            }

            for (i in otherParams.indices) {
                val param = it.getOrNull(i) ?: return false
                val otherParam = otherParams[i]
                if (!otherParam.ty.contravariantOf(param.ty, context, flags)) {
                    return false
                }
            }
        }

        val otherReturnTy = other.returnTy

        return if (otherReturnTy != null) {
            returnTy?.contravariantOf(otherReturnTy, context, flags) ?: true
        } else returnTy == null
    }
}

class FunSignature(colonCall: Boolean,
                   override val returnTy: ITy?,
                   override val varargTy: ITy?,
                   params: Array<LuaParamInfo>?,
                   tyParameters: Array<TyParameter>? = null
) : FunSignatureBase(colonCall, params, tyParameters) {

    companion object {
        fun create(colonCall: Boolean, functionTy: LuaDocFunctionTy): IFunSignature {
            return FunSignature(
                    colonCall,
                    functionTy.returnType,
                    functionTy.varargParam,
                    functionTy.params as? Array<LuaParamInfo>, // Casting due to https://youtrack.jetbrains.com/issue/KT-40034
                    functionTy.genericDefList.map { TyParameter(it) }.toTypedArray()
            )
        }

        fun serialize(sig: IFunSignature, stream: StubOutputStream) {
            stream.writeBoolean(sig.colonCall)
            stream.writeTyNullable(sig.returnTy)
            stream.writeTyNullable(sig.varargTy)
            stream.writeParamInfoArrayNullable(sig.params)
        }

        fun deserialize(stream: StubInputStream): IFunSignature {
            val colonCall = stream.readBoolean()
            val ret = stream.readTyNullable()
            val varargTy = stream.readTyNullable()
            val params = stream.readParamInfoArrayNullable()
            return FunSignature(colonCall, ret, varargTy, params)
        }
    }
}

interface ITyFunction : ITy {
    val mainSignature: IFunSignature
    val signatures: Array<IFunSignature>
}

abstract class TyFunction : Ty(TyKind.Function), ITyFunction {

    override fun equals(other: Any?): Boolean {
        if (other is ITyFunction) {
            if (mainSignature != other.mainSignature)
                return false
           return signatures.indices.none { signatures[it] != other.signatures.getOrNull(it) }
        }
        return false
    }

    override fun equals(other: ITy, context: SearchContext): Boolean {
        if (this === other) {
            return true
        }

        val resolvedOther = Ty.resolve(other, context)

        if (resolvedOther is ITyFunction) {
            if (!mainSignature.equals(resolvedOther.mainSignature, context))
                return false

            return signatures.size == resolvedOther.signatures.size
                    && signatures.asSequence().zip(resolvedOther.signatures.asSequence()).all { (signature, otherSignature) ->
                signature.equals(otherSignature, context)
            }
        }

        return false
    }

    override fun hashCode(): Int {
        var code = mainSignature.hashCode()
        signatures.forEach {
            code += it.hashCode() * 31
        }
        return code
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        if (super.contravariantOf(other, context, flags)) return true

        var matched = false

        processSignatures(context, Processor { sig ->
            if (other == Ty.FUNCTION) {
                val multipleResults = sig.returnTy as? TyMultipleResults
                matched = multipleResults?.variadic == true && multipleResults.list.size == 1 && multipleResults.list.first() is TyUnknown
                        && (sig.params?.size ?: 0) == 0 && sig.varargTy is TyUnknown
            } else {
                other.processSignatures(context, Processor { otherSig ->
                    matched = sig.contravariantOf(otherSig, context, flags)
                    !matched
                })
            }
            !matched
        })

        return matched
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        return substitutor.substitute(this)
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitFun(this)
    }

    override fun processSignatures(context: SearchContext, processor: Processor<IFunSignature>): Boolean {
        // Overloads will always be more (or as) specific as the main signature, so we visit them first.
        for (signature in signatures) {
            if (!processor.process(signature)) {
                return false
            }
        }

        return processor.process(mainSignature)
    }
}

class TyPsiFunction(private val colonCall: Boolean, val psi: LuaFuncBodyOwner, flags: Int = 0) : TyFunction() {
    init {
        this.flags = flags
        if (colonCall) {
            this.flags = this.flags or TyFlags.SELF_FUNCTION
        }
    }

    override val mainSignature: IFunSignature by lazy {

        object : FunSignatureBase(colonCall, psi.params, psi.tyParams) {
            override val returnTy: ITy by lazy {
                val context = SearchContext.get(psi.project)
                var returnTy = context.withMultipleResults { psi.guessReturnType(context) ?: Ty.UNKNOWN }
                /**
                 * todo optimize this bug solution
                 * local function test()
                 *      return test
                 * end
                 * -- will crash after type `test`
                 */
                if (returnTy is TyPsiFunction && returnTy.psi == psi) {
                    returnTy = UNKNOWN
                }

                returnTy
            }

            override val varargTy: ITy?
                get() = psi.varargType
        }
    }

    override val signatures: Array<IFunSignature> by lazy {
        psi.overloads
    }
}

class TyDocPsiFunction(func: LuaDocFunctionTy) : TyFunction() {
    private val main = FunSignature.create(false, func)
    override val mainSignature: IFunSignature = main
    override val signatures: Array<IFunSignature> = emptyArray()
}

class TySerializedFunction(override val mainSignature: IFunSignature,
                           override val signatures: Array<IFunSignature>,
                           flags: Int = 0) : TyFunction() {
    init {
        this.flags = flags
    }
}

object TyFunctionSerializer : TySerializer<ITyFunction>() {
    override fun deserializeTy(flags: Int, stream: StubInputStream): ITyFunction {
        val mainSig = FunSignature.deserialize(stream)
        val arr = stream.readSignatures()
        return TySerializedFunction(mainSig, arr, flags)
    }

    override fun serializeTy(ty: ITyFunction, stream: StubOutputStream) {
        FunSignature.serialize(ty.mainSignature, stream)
        stream.writeSignatures(ty.signatures)
    }
}
