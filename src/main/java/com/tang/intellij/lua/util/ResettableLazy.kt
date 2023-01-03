/*
 * Copyright (c) 2022
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

import kotlin.reflect.KProperty

fun <T> conditionallyCached(condition: () -> Boolean, compute: () -> T) = ConditionallyCachedDelegate(condition, compute)

class ConditionallyCachedDelegate<T>(private val condition: () -> Boolean, private val compute: () -> T) {
    private val lazyComputed = lazy(compute)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (lazyComputed.isInitialized() || condition()) {
            return lazyComputed.value
        } else {
            return compute()
        }
    }
}
