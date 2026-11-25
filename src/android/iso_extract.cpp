/**
 * @file        android/iso_extract.cpp
 *
 * @brief       Extracts an Xbox 360 (GDFX) ISO into a folder, reusing the
 * SDK's own disc-image reader.
 */
#include "iso_extract.h"

#include <cstdio>
#include <vector>

#include <rex/filesystem/devices/disc_image_device.h>
#include <rex/filesystem/entry.h>
#include <rex/logging.h>
#include <rex/memory/mapped_memory.h>

namespace tekken6::android {

namespace {

struct FileToExtract {
  rex::filesystem::Entry* entry;
  std::filesystem::path relative_path;
};

void CollectFiles(rex::filesystem::Entry* entry, const std::filesystem::path& relative_path,
                  std::vector<FileToExtract>& out, uint64_t& total_bytes) {
  for (const auto& child : entry->children()) {
    std::filesystem::path child_rel = relative_path / child->name();
    if (child->attributes() & rex::filesystem::kFileAttributeDirectory) {
      CollectFiles(child.get(), child_rel, out, total_bytes);
    } else {
      out.push_back(FileToExtract{child.get(), child_rel});
      total_bytes += child->size();
    }
  }
}

bool WriteFile(rex::filesystem::Entry* entry, const std::filesystem::path& dest,
               uint64_t& bytes_written, uint64_t total_bytes,
               const ExtractProgressCallback& progress) {
  auto mapped = entry->OpenMapped(rex::memory::MappedMemory::Mode::kRead, 0, entry->size());
  if (!mapped) {
    REXLOG_ERROR("ISO extract: cannot map '{}'", entry->name());
    return false;
  }
  std::error_code ec;
  std::filesystem::create_directories(dest.parent_path(), ec);
  FILE* out = std::fopen(dest.string().c_str(), "wb");
  if (!out) {
    REXLOG_ERROR("ISO extract: cannot create '{}'", dest.string());
    return false;
  }
  const uint8_t* data = mapped->data();
  size_t remaining = mapped->size();
  constexpr size_t kChunk = 4 * 1024 * 1024;
  while (remaining > 0) {
    size_t chunk = std::min(remaining, kChunk);
    if (std::fwrite(data, 1, chunk, out) != chunk) {
      REXLOG_ERROR("ISO extract: write failed for '{}'", dest.string());
      std::fclose(out);
      return false;
    }
    data += chunk;
    remaining -= chunk;
    bytes_written += chunk;
    if (progress && total_bytes > 0) {
      progress(static_cast<float>(bytes_written) / static_cast<float>(total_bytes),
               entry->name());
    }
  }
  std::fclose(out);
  return true;
}

}  // namespace

int ExtractIsoImage(const std::filesystem::path& iso_path,
                    const std::filesystem::path& dest_dir,
                    const ExtractProgressCallback& progress) {
  rex::filesystem::DiscImageDevice device("game:", iso_path);
  if (!device.Initialize()) {
    REXLOG_ERROR("ISO extract: '{}' is not a readable Xbox 360 disc image", iso_path.string());
    return 1;
  }
  const auto* root = device.root();
  if (!root) {
    REXLOG_ERROR("ISO extract: no root entry in '{}'", iso_path.string());
    return 2;
  }

  std::vector<FileToExtract> files;
  uint64_t total_bytes = 0;
  CollectFiles(const_cast<rex::filesystem::Entry*>(root), {}, files, total_bytes);
  REXLOG_INFO("ISO extract: {} files, {} bytes from '{}'", files.size(), total_bytes,
              iso_path.string());
  if (files.empty()) {
    return 3;
  }

  std::error_code ec;
  std::filesystem::create_directories(dest_dir, ec);
  uint64_t bytes_written = 0;
  for (const auto& file : files) {
    std::filesystem::path dest = dest_dir / file.relative_path;
    if (!WriteFile(file.entry, dest, bytes_written, total_bytes, progress)) {
      return 4;
    }
  }
  if (progress) {
    progress(1.0f, "");
  }
  return 0;
}

}  // namespace tekken6::android
