import { useEffect, useState } from "react";

export function useCountUp(target, { duration = 900, delay = 0 } = {}) {
  const [value, setValue] = useState(0);

  useEffect(() => {
    let raf = null;
    let timer = null;

    const run = () => {
      const t0 = performance.now();

      const tick = (now) => {
        const progress = Math.min(1, (now - t0) / duration);
        const eased = 1 - Math.pow(1 - progress, 3);
        setValue(Math.round(target * eased));
        if (progress < 1) {
          raf = requestAnimationFrame(tick);
        } else {
          setValue(target);
        }
      };

      raf = requestAnimationFrame(tick);
    };

    timer = setTimeout(run, delay);

    return () => {
      if (timer) clearTimeout(timer);
      if (raf) cancelAnimationFrame(raf);
    };
  }, [target, duration, delay]);

  return value;
}