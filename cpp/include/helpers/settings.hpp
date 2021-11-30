/*
 * Copyright 2018 The Android Open Source Project
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

#pragma once

#include "helpers/thread.hpp"

#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

namespace rive_android
{

	class Settings
	{
	private:
		// Allows construction with std::unique_ptr from a static method, but
		// disallows construction outside of the class since no one else can
		// construct a ConstructorTag
		struct ConstructorTag
		{
		};

	public:
		explicit Settings(ConstructorTag) : mHotPocket(false) {}

		static Settings* getInstance();

		using Listener = std::function<void()>;

		void addListener(Listener listener);

		void setPreference(std::string key, std::string value);

		bool getHotPocket() const;

		bool isTraceEnabled() const;

	private:
		void notifyListeners();

		mutable std::mutex mMutex;
		std::vector<Listener> mListeners GUARDED_BY(mMutex);
		std::atomic<bool> mHotPocket;
		std::atomic<bool> mIsTraceEnabled;
	};

} // namespace rive_android
