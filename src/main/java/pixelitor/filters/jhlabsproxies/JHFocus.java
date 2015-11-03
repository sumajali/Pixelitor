/*
 * Copyright 2015 Laszlo Balazs-Csiki
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */
package pixelitor.filters.jhlabsproxies;

import com.jhlabs.image.VariableBlurFilter;
import pixelitor.filters.FilterWithParametrizedGUI;
import pixelitor.filters.gui.BooleanParam;
import pixelitor.filters.gui.GroupedRangeParam;
import pixelitor.filters.gui.ImagePositionParam;
import pixelitor.filters.gui.ParamSet;
import pixelitor.filters.gui.RangeParam;
import pixelitor.utils.BlurredEllipse;
import pixelitor.utils.ImageUtils;

import java.awt.image.BufferedImage;

/**
 * JH Focus filter based on the JHLabs VariableBlurFilter
 */
public class JHFocus extends FilterWithParametrizedGUI {
    private final ImagePositionParam center = new ImagePositionParam("Focused Area Center");

    private final GroupedRangeParam radius = new GroupedRangeParam("Focused Area Radius (Pixels)", 1, 1000, 200, false);
    private final RangeParam penumbraMultiplier = new RangeParam("Transition Area Radius (Focused Area Radius %)", 1, 500, 100);
    private final GroupedRangeParam blurRadius = new GroupedRangeParam("Blur Radius", 0, 50, 10);
    private final RangeParam numberOfIterations = new RangeParam("Number of Blur Iterations", 1, 10, 3);
    private final BooleanParam invert = new BooleanParam("Invert", false);
    private final BooleanParam hpSharpening = BooleanParam.createParamForHPSharpening();

    private FocusImpl filter;

    public JHFocus() {
        super("Focus", true, false);
        setParamSet(new ParamSet(
                center,
                radius,
                penumbraMultiplier,
                blurRadius,
                numberOfIterations,
                invert,
                hpSharpening
        ));
    }

    @Override
    public BufferedImage doTransform(BufferedImage src, BufferedImage dest) {
        int hRadius = blurRadius.getValue(0);
        int vRadius = blurRadius.getValue(1);
        if ((hRadius == 0) && (vRadius == 0)) {
            return src;
        }

        // TODO copied from JHBoxBlur, but is it necessary?
        if ((src.getWidth() == 1) || (src.getHeight() == 1)) {
            // otherwise we get ArrayIndexOutOfBoundsException in BoxBlurFilter
            return src;
        }

        if (filter == null) {
            filter = new FocusImpl();
        }

        filter.setCenter(
                src.getWidth() * center.getRelativeX(),
                src.getHeight() * center.getRelativeY()
        );
        filter.setRadius(radius.getValueAsDouble(0),
                radius.getValueAsDouble(1),
                (penumbraMultiplier.getValueAsDouble() + 100.0) / 100.0);
        filter.setInverted(invert.isChecked());

        // TODO unlike BoxBlurFilter, VariableBlurFilter supports only integer radii
        filter.setHRadius(blurRadius.getValueAsFloat(0));
        filter.setVRadius(blurRadius.getValueAsFloat(1));

        filter.setIterations(numberOfIterations.getValue());
        filter.setPremultiplyAlpha(false);

        dest = filter.filter(src, dest);

        if (hpSharpening.isChecked()) {
            dest = ImageUtils.getHighPassSharpenedImage(src, dest);
        }

        return dest;
    }

    private static class FocusImpl extends VariableBlurFilter {
        private double cx;
        private double cy;
        private double innerRadiusX;
        private double innerRadiusY;
        private double outerRadiusX;
        private double outerRadiusY;
        private boolean inverted;

        private BlurredEllipse ellipse;

        public void setCenter(double cx, double cy) {
            this.cx = cx;
            this.cy = cy;
        }

        public void setRadius(double innerRadiusX, double innerRadiusY, double penumbraMultiplier) {
            this.innerRadiusX = innerRadiusX;
            this.innerRadiusY = innerRadiusY;
            this.outerRadiusX = innerRadiusX * penumbraMultiplier;
            this.outerRadiusY = innerRadiusY * penumbraMultiplier;
        }

        @Override
        protected float blurRadiusAt(int x, int y) {
            double outside = ellipse.isOutside(x, y);
            if (inverted) {
                return (float) (1 - outside);
            }
            return (float) outside;
        }

        @Override
        public BufferedImage filter(BufferedImage src, BufferedImage dst) {
            ellipse = new BlurredEllipse(cx, cy,
                    innerRadiusX, innerRadiusY, outerRadiusX, outerRadiusY);
            return super.filter(src, dst);
        }

        public void setInverted(boolean inverted) {
            this.inverted = inverted;
        }
    }
}