package dev.candycanecazoo.hellstorm.client;

import com.eliotlash.mclib.math.IValue;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.EasingType;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.core.keyframe.KeyframeStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** GeckoLib animations made from others: played backward, or sped up to a length. */
final class Animations {
    private Animations() {}

    /**
     * {@code animation} played from its end to its start, exactly: each keyframe's motion runs the other way, under the
     * mirror of its easing, since an ease in played backward is the matching ease out.
     */
    static Animation reversed(Animation animation, String name) {
        double length = animation.length();
        return new Animation(name, length, animation.loopType(), bones(animation, stack -> reversed(stack, length)), animation.keyFrames());
    }

    /** {@code animation} sped up or slowed down to last {@code ticks}. */
    static Animation fitted(Animation animation, double ticks) {
        double scale = ticks / animation.length();
        return new Animation(animation.name(), ticks, animation.loopType(), bones(animation, stack -> scaled(stack, scale)), animation.keyFrames());
    }

    private static BoneAnimation[] bones(Animation animation, UnaryOperator<List<Keyframe<IValue>>> change) {
        BoneAnimation[] bones = animation.boneAnimations().clone();
        for (int i = 0; i < bones.length; i++) {
            BoneAnimation bone = bones[i];
            bones[i] = new BoneAnimation(bone.boneName(), stack(bone.rotationKeyFrames(), change), stack(bone.positionKeyFrames(), change),
                    stack(bone.scaleKeyFrames(), change));
        }
        return bones;
    }

    private static KeyframeStack<Keyframe<IValue>> stack(KeyframeStack<Keyframe<IValue>> stack, UnaryOperator<List<Keyframe<IValue>>> change) {
        return new KeyframeStack<>(change.apply(stack.xKeyframes()), change.apply(stack.yKeyframes()), change.apply(stack.zKeyframes()));
    }

    /**
     * Each keyframe runs from the previous one's value to its own over its length. Backward, a channel that ends before the
     * animation does first holds its last value for the difference.
     */
    private static List<Keyframe<IValue>> reversed(List<Keyframe<IValue>> frames, double length) {
        if (frames.isEmpty()) {
            return frames;
        }
        double total = frames.stream().mapToDouble(Keyframe::length).sum();
        IValue last = frames.get(frames.size() - 1).endValue();
        List<Keyframe<IValue>> out = new ArrayList<>();
        out.add(new Keyframe<>(Math.max(0, length - total), last, last));
        for (int i = frames.size() - 1; i >= 0; i--) {
            Keyframe<IValue> frame = frames.get(i);
            List<IValue> args = new ArrayList<>(frame.easingArgs());
            if (frame.easingType() == EasingType.CATMULLROM) {
                // Its arguments are the values before and after; backward, they swap.
                Collections.reverse(args);
            }
            out.add(new Keyframe<>(frame.length(), frame.endValue(), frame.startValue(), mirrored(frame.easingType()), args));
        }
        return out;
    }

    private static List<Keyframe<IValue>> scaled(List<Keyframe<IValue>> frames, double scale) {
        return frames.stream().map(frame -> new Keyframe<>(frame.length() * scale, frame.startValue(), frame.endValue(), frame.easingType(),
                frame.easingArgs())).toList();
    }

    /** GeckoLib defines each ease out as the ease in turned around, so they mirror each other; in-outs mirror themselves. */
    private static EasingType mirrored(EasingType easing) {
        for (Map.Entry<String, EasingType> entry : EasingType.EASING_TYPES.entrySet()) {
            if (entry.getValue() == easing) {
                String name = entry.getKey();
                String mirror = name.startsWith("easeinout") ? name
                        : name.startsWith("easein") ? "easeout" + name.substring("easein".length())
                        : name.startsWith("easeout") ? "easein" + name.substring("easeout".length())
                        : name;
                return EasingType.EASING_TYPES.getOrDefault(mirror, easing);
            }
        }
        return easing;
    }
}
