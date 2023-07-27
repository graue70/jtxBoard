/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {

    ext {
        version_core = "1.12.0-beta01"
        version_coroutine = "1.7.3"
        version_appcompat = "1.6.1"
        version_gradle = '8.1.0'
        version_kotlin = "1.8.22"
        version_room = "2.5.2"
        version_work = "2.8.1"
        version_billing = "6.0.1"
        version_review = "2.0.1"
        version_compose = "1.4.3"
        version_about_libraries = "10.8.3"
        version_huawei = "1.8.1.300"
        version_huawei_iap = "6.10.0.300"
        version_accompanist = "0.31.5-beta"
        version_osmdroid = "6.1.16"
    }
}


tasks.register('clean', Delete) {
    delete rootProject.buildDir
}
