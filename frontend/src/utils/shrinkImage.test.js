import { describe, expect, it, vi } from 'vitest'
import { MAX_SIDE, SKIP_BELOW_BYTES, shrinkFormDataImages, shrinkImage } from './shrinkImage'

const photo = (bytes, type = 'image/jpeg', name = 'IMG_2041.jpeg') => new File([new Uint8Array(bytes)], name, { type })

// The browser's decoder and canvas are not available under test; stand-ins record what was asked.
const fakes = (width, height, outBytes = 200 * 1024) => {
  const close = vi.fn()
  const decode = vi.fn(async () => ({ width, height, close }))
  const encode = vi.fn(async () => new Blob([new Uint8Array(outBytes)], { type: 'image/jpeg' }))
  return { decode, encode, close }
}

describe('shrinkImage', () => {
  it('shrinks a big phone photo to 1600 px on the long side, as a JPEG', async () => {
    const f = fakes(4032, 3024)
    const out = await shrinkImage(photo(5 * 1024 * 1024), f)

    expect(f.encode).toHaveBeenCalledWith(expect.anything(), MAX_SIDE, 1200, expect.any(Number))
    expect(out.type).toBe('image/jpeg')
    expect(out.name).toBe('IMG_2041.jpg')
    expect(out.size).toBe(200 * 1024)
    expect(f.close).toHaveBeenCalled()
  })

  it('never enlarges a small-dimension image', async () => {
    const f = fakes(800, 600)
    await shrinkImage(photo(SKIP_BELOW_BYTES + 1), f)
    expect(f.encode).toHaveBeenCalledWith(expect.anything(), 800, 600, expect.any(Number))
  })

  it('leaves small files, GIFs and non-images untouched', async () => {
    const f = fakes(4000, 3000)
    const small = photo(SKIP_BELOW_BYTES)
    const gif = photo(2 * 1024 * 1024, 'image/gif', 'a.gif')
    const pdf = photo(2 * 1024 * 1024, 'application/pdf', 'a.pdf')

    expect(await shrinkImage(small, f)).toBe(small)
    expect(await shrinkImage(gif, f)).toBe(gif)
    expect(await shrinkImage(pdf, f)).toBe(pdf)
    expect(f.decode).not.toHaveBeenCalled()
  })

  it('sends the original when this browser cannot decode it (e.g. HEIC)', async () => {
    const original = photo(3 * 1024 * 1024, 'image/png', 'scan.png')
    const out = await shrinkImage(original, { decode: async () => { throw new Error('unsupported') }, encode: vi.fn() })
    expect(out).toBe(original)
  })

  it('sends the original when the re-encoded copy would not be smaller', async () => {
    const original = photo(400 * 1024)
    expect(await shrinkImage(original, fakes(1000, 800, 500 * 1024))).toBe(original)
  })
})

describe('shrinkFormDataImages', () => {
  it('shrinks every image in the form and keeps the other fields as they were', async () => {
    const fd = new FormData()
    fd.append('name', 'Ravi')
    fd.append('licensePhoto', photo(4 * 1024 * 1024, 'image/jpeg', 'dl.jpg'))
    fd.append('photo', photo(6 * 1024 * 1024, 'image/png', 'me.png'))

    const out = await shrinkFormDataImages(fd, fakes(4000, 3000))

    expect(out.get('name')).toBe('Ravi')
    expect(out.get('licensePhoto').size).toBe(200 * 1024)
    expect(out.get('licensePhoto').name).toBe('dl.jpg')
    expect(out.get('photo').name).toBe('me.jpg')
    expect(out.get('photo').type).toBe('image/jpeg')
  })

  it('returns a form without files as it is', async () => {
    const fd = new FormData()
    fd.append('name', 'Ravi')
    expect(await shrinkFormDataImages(fd)).toBe(fd)
  })
})
