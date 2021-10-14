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

#include "swappy/swappy_common.h"

#include <cstdint>
#include <unordered_map>
#include <mutex>

// Enable thread safety attributes only with clang.
// The attributes can be safely erased when compiling with other compilers.
#if defined(__clang__) && (!defined(SWIG))
#define THREAD_ANNOTATION_ATTRIBUTE__(x) __attribute__((x))
#else
#define THREAD_ANNOTATION_ATTRIBUTE__(x) // no-op
#endif

#define GUARDED_BY(x) \
  THREAD_ANNOTATION_ATTRIBUTE__(guarded_by(x))

#define REQUIRES(...) \
  THREAD_ANNOTATION_ATTRIBUTE__(requires_capability(__VA_ARGS__))

namespace samples
{

  enum class Affinity
  {
    None,
    Even,
    Odd
  };

  int32_t getNumCpus();

  void setAffinity(int32_t cpu);

  void setAffinity(Affinity affinity);

  // This is a minimal demonstration of a thread manager that uses pthreads.
  // It is not intended for production use!
  struct ThreadManager
  {
    std::mutex threadMapMutex;
    std::unordered_map<SwappyThreadId, pthread_t> threads;
    ThreadManager() {}
    ThreadManager(const ThreadManager &) = delete;
    static std::atomic<SwappyThreadId> nextId;

  public:
    static ThreadManager &Instance();
    int Start(SwappyThreadId *thread_id, void *(*thread_func)(void *), void *user_data);
    void Join(SwappyThreadId thread_id);
    bool Joinable(SwappyThreadId thread_id);
  };

}
