/**
 * @file        android/iso_extract.h
 *
 * @brief       Extracts an Xbox 360 (GDFX) ISO into a folder, reusing the
 * SDK's own disc-image reader (rex/filesystem/devices/disc_image_device.h).
 */
#pragma once

#include <filesystem>
#include <functional>
#include <string>

namespace tekken6::android {

// Progress callback: fraction 0..1, and the file currently being written.
// Return 0 on success, non-zero on failure.
using ExtractProgressCallback = std::function<void(float fraction, const std::string& current_file)>;

int ExtractIsoImage(const std::filesystem::path& iso_path,
                    const std::filesystem::path& dest_dir,
                    const ExtractProgressCallback& progress = {});

}  // namespace tekken6::android
