// Generated playlist covers: a three-colour gradient with 1-4 letters on top,
// like the initials avatars contact apps use. Rendered on a canvas, uploaded as PNG.

export type GradientSpec = {
    angle: number; // degrees
    colors: [string, string, string];
};

export const MAX_COVER_LETTERS = 4;
const COVER_SIZE = 600;

const randomBetween = (min: number, max: number) => min + Math.random() * (max - min);

// Hues that sit near each other (analogous) or spread out (triad), with enough
// saturation to stand out in a grid but dark enough for white letters.
export const randomGradient = (): GradientSpec => {
    const base = Math.random() * 360;
    const spread = Math.random() < 0.6 ? randomBetween(28, 60) : randomBetween(90, 130);
    const hues = [base, base + spread, base + spread * 2].map((h) => h % 360);
    const colors = hues.map((h, i) => {
        const s = Math.round(randomBetween(62, 86));
        const l = Math.round(randomBetween(40, 56) - i * 3);
        return `hsl(${Math.round(h)}, ${s}%, ${l}%)`;
    }) as [string, string, string];
    return { angle: Math.round(randomBetween(0, 360)), colors };
};

// "Programming Jams" -> "PJ", "chill" -> "C", "80s Mix" -> "8M". Contacts-style: up to two words.
export const initialsFor = (name: string) => {
    const words = name
        .replace(/[^\p{L}\p{N}\s]/gu, ' ')
        .split(/\s+/)
        .filter(Boolean);
    return words
        .slice(0, 2)
        .map((w) => Array.from(w)[0])
        .join('')
        .toUpperCase();
};

export const cleanLetters = (value: string) =>
    Array.from(value.replace(/\s+/g, '')).slice(0, MAX_COVER_LETTERS).join('').toUpperCase();

const FONT_SCALE: Record<number, number> = { 0: 0, 1: 0.52, 2: 0.42, 3: 0.32, 4: 0.25 };

export const drawGradientCover = (
    canvas: HTMLCanvasElement,
    spec: GradientSpec,
    letters: string,
    size = COVER_SIZE,
) => {
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Gradient line through the centre at the chosen angle, long enough to cover the corners.
    const rad = (spec.angle * Math.PI) / 180;
    const half = (size / 2) * Math.SQRT2;
    const cx = size / 2;
    const dx = Math.cos(rad) * half;
    const dy = Math.sin(rad) * half;
    const gradient = ctx.createLinearGradient(cx - dx, cx - dy, cx + dx, cx + dy);
    gradient.addColorStop(0, spec.colors[0]);
    gradient.addColorStop(0.5, spec.colors[1]);
    gradient.addColorStop(1, spec.colors[2]);
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, size, size);

    // Soft light from the top-left so it doesn't look flat.
    const glow = ctx.createRadialGradient(
        size * 0.25,
        size * 0.2,
        0,
        size * 0.25,
        size * 0.2,
        size,
    );
    glow.addColorStop(0, 'rgba(255, 255, 255, 0.22)');
    glow.addColorStop(0.6, 'rgba(255, 255, 255, 0)');
    ctx.fillStyle = glow;
    ctx.fillRect(0, 0, size, size);

    const text = cleanLetters(letters);
    const count = Array.from(text).length;
    if (!count) return;

    ctx.font = `700 ${Math.round(size * FONT_SCALE[count])}px Inter, "Segoe UI", Roboto, sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.shadowColor = 'rgba(0, 0, 0, 0.28)';
    ctx.shadowBlur = size * 0.03;
    ctx.shadowOffsetY = size * 0.01;
    ctx.fillStyle = '#ffffff';
    ctx.fillText(text, size / 2, size / 2 + size * 0.02);
};

export const gradientCoverBlob = (spec: GradientSpec, letters: string): Promise<Blob> =>
    new Promise((resolve, reject) => {
        const canvas = document.createElement('canvas');
        drawGradientCover(canvas, spec, letters);
        canvas.toBlob((blob) =>
            blob ? resolve(blob) : reject(new Error('Could not render cover')),
        );
    });

export const gradientCoverFile = async (spec: GradientSpec, letters: string) =>
    new File([await gradientCoverBlob(spec, letters)], 'cover.png', { type: 'image/png' });
