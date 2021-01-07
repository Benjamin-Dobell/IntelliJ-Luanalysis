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

package com.tang.intellij.lua.psi;

import com.intellij.psi.stubs.StubElement;
import com.tang.intellij.lua.comment.psi.LuaDocTagParam;
import com.tang.intellij.lua.comment.psi.LuaDocTagReturn;
import com.tang.intellij.lua.comment.psi.LuaDocTagVararg;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.stubs.LuaFuncBodyOwnerStub;
import com.tang.intellij.lua.ty.ITy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * #local function
 * #function
 * #lambda function
 * #class method
 *
 * Created by TangZX on 2016/12/9.
 */
public interface LuaFuncBodyOwner<Stub extends LuaFuncBodyOwnerStub> extends LuaParametersOwner<Stub>, LuaTypeGuessable {

    @Nullable
    LuaFuncBody getFuncBody();

    @Nullable
    default LuaDocTagReturn getTagReturn() {
        return LuaPsiImplUtilKt.getTagReturn(this);
    }

    @Nullable
    default LuaDocTagVararg getTagVararg() {
        return LuaPsiImplUtilKt.getTagVararg(this);
    }

    /**
     * 返回类型
     */
    @Nullable
    ITy guessReturnType(@NotNull SearchContext searchContext);

    @Nullable
    ITy guessParentType(@NotNull SearchContext context);

    @Nullable
    default ITy getVarargType() {
        return LuaPsiImplUtilKt.getVarargTy(this);
    }

    @NotNull
    LuaParamInfo[] getParams();

    default String getParamSignature() {
        return LuaPsiImplUtilKt.getParamSignature(this);
    }
}
