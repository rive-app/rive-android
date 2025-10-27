#pragma once

#include <cstdint>

namespace rive_android
{
class RenderingStats
{
    double mLatestMean = 0, mLatestVar = 0;
    double mRunningMean = 0, mRunningVar = 0;
    size_t mN = 0;
    size_t mNumToAvg;

public:
    explicit RenderingStats(size_t numToAvg) : mNumToAvg(numToAvg) {}

    // Add a sample.
    // When mNumToAvg samples have been calculated, store the mean and
    // average and start again.
    void add(double x)
    {
        ++mN;
        auto prevMean = mRunningMean;
        mRunningMean = ((mN - 1) * mRunningMean + x) / mN;
        if (mN > 1)
        {
            mRunningVar = ((mN - 2) * mRunningVar) / (mN - 1) +
                          (x - prevMean) * (x - prevMean) / mN;
        }
        if (mN == mNumToAvg)
            restart();
    }

    void restart()
    {
        mLatestMean = mRunningMean;
        mLatestVar = mRunningVar;
        mN = 0;
    }

    [[nodiscard]] double mean() const { return mLatestMean; }

    [[nodiscard]] double var() const { return mLatestVar; }
};
} // namespace rive_android
