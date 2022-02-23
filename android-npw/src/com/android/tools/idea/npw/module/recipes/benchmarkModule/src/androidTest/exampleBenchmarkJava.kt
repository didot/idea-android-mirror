/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.npw.module.recipes.benchmarkModule.src.androidTest

fun exampleBenchmarkJava(className: String, packageName: String) =
"""package $packageName;

import android.util.Log;

import androidx.benchmark.junit4.BenchmarkRule;
import androidx.benchmark.BenchmarkState;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
* Benchmark, which will execute on an Android device.
*
* The while loop will measure the contents of the loop, and Studio will
* output the result. Modify your code to see how it affects performance.
*/
@RunWith(AndroidJUnit4.class)
public class $className {

    @Rule
    public BenchmarkRule mBenchmarkRule = new BenchmarkRule();

    @Test
    public void log() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            Log.d("LogBenchmark", "the cost of writing this log method will be measured");
        }
    }
}
"""
