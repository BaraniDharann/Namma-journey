// Phone photos are 3-12 MB, and drivers often upload over mobile data, where that is slow and
// fails halfway. So images are shrunk in the browser before they are sent. The server compresses
// them again to its own size per kind (ImageCompressor), so this only has to stay big enough for
// the largest of those: 1600 px for licences and RCs, whose fine print must stay readable.
export const MAX_SIDE = 1600
export const QUALITY = 0.88
/** Already small enough that shrinking would not save a meaningful upload. */
export const SKIP_BELOW_BYTES = 300 * 1024

// GIF is left alone: the server takes its first frame either way.
const SHRINKABLE = /^image\/(jpeg|jpg|png|webp)$/i

/** Decodes upright: createImageBitmap applies the EXIF orientation by default. */
const decodeBitmap = (file) => createImageBitmap(file)

async function encodeJpeg(bitmap, width, height, quality) {
  const canvas = typeof OffscreenCanvas !== 'undefined'
    ? new OffscreenCanvas(width, height)
    : Object.assign(document.createElement('canvas'), { width, height })
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#fff' // transparency becomes white, as on paper (matches the server)
  ctx.fillRect(0, 0, width, height)
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(bitmap, 0, 0, width, height)
  if (canvas.convertToBlob) return canvas.convertToBlob({ type: 'image/jpeg', quality })
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
}

/**
 * A smaller JPEG copy of an image file, or the file itself when it is not a shrinkable image,
 * already small, cannot be decoded here (e.g. HEIC in Chrome), or would not get smaller.
 * Never throws: the server can always handle the original.
 */
export async function shrinkImage(file, { decode = decodeBitmap, encode = encodeJpeg } = {}) {
  if (!(file instanceof Blob) || !SHRINKABLE.test(file.type) || file.size <= SKIP_BELOW_BYTES) return file
  let bitmap
  try {
    bitmap = await decode(file)
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height))
    const width = Math.max(1, Math.round(bitmap.width * scale))
    const height = Math.max(1, Math.round(bitmap.height * scale))
    const blob = await encode(bitmap, width, height, QUALITY)
    if (!blob || blob.size >= file.size) return file
    const name = (file.name || 'photo').replace(/\.[^.]*$/, '') + '.jpg'
    return new File([blob], name, { type: 'image/jpeg', lastModified: Date.now() })
  } catch {
    return file
  } finally {
    bitmap?.close?.()
  }
}

/**
 * The same form with every image shrunk. One at a time, so a low-end phone never holds several
 * decoded 12-megapixel photos at once.
 */
export async function shrinkFormDataImages(formData, options) {
  const entries = [...formData.entries()]
  if (!entries.some(([, v]) => v instanceof Blob)) return formData
  const out = new FormData()
  for (const [key, value] of entries) {
    if (value instanceof Blob) {
      const file = await shrinkImage(value, options)
      out.append(key, file, file.name)
    } else {
      out.append(key, value)
    }
  }
  return out
}
