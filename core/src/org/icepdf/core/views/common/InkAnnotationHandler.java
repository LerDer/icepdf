/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.views.common;

import org.icepdf.core.AnnotationCallback;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.InkAnnotation;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.swing.AbstractPageViewComponent;
import org.icepdf.core.views.swing.annotations.AbstractAnnotationComponent;
import org.icepdf.core.views.swing.annotations.AnnotationComponentFactory;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;
import java.util.logging.Logger;

/**
 * InkAnnotationHandler tool is responsible for painting representation of
 * a ink on the screen as the mouse is dragged around the page.  The points
 * that make up the mouse path are then used to create the InkAnnotation and
 * respective annotation component.
 * <p/>
 * The addition of the Annotation object to the page is handled by the
 * annotation callback.
 *
 * @since 5.0
 */
public class InkAnnotationHandler implements ToolHandler {

    private static final Logger logger =
            Logger.getLogger(LineAnnotationHandler.class.toString());

    // parent page component
    protected AbstractPageViewComponent pageViewComponent;
    protected DocumentViewController documentViewController;
    protected DocumentViewModel documentViewModel;

    // need to make the stroke cap, thickness configurable. Or potentially
    // static from the lineAnnotationHandle so it would look like the last
    // settings where remembered.
    protected static BasicStroke stroke = new BasicStroke(1.0f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            1.0f);
    protected static Color lineColor = Color.GREEN;

    // start and end point
    protected GeneralPath inkPath;

    protected BorderStyle borderStyle = new BorderStyle();

    /**
     * New Text selection handler.  Make sure to correctly and and remove
     * this mouse and text listeners.
     *
     * @param pageViewComponent page component that this handler is bound to.
     * @param documentViewModel view model.
     */
    public InkAnnotationHandler(DocumentViewController documentViewController,
                                AbstractPageViewComponent pageViewComponent,
                                DocumentViewModel documentViewModel) {
        this.documentViewController = documentViewController;
        this.pageViewComponent = pageViewComponent;
        this.documentViewModel = documentViewModel;
    }

    public void paintTool(Graphics g) {
        if (inkPath != null) {
            Graphics2D gg = (Graphics2D) g;
            Color oldColor = gg.getColor();
            Stroke oldStroke = gg.getStroke();
            gg.setColor(lineColor);
            gg.setStroke(stroke);
            gg.draw(inkPath);
            gg.setColor(oldColor);
            gg.setStroke(oldStroke);
        }
    }

    public void mouseClicked(MouseEvent e) {

    }

    public void mousePressed(MouseEvent e) {
        // annotation selection box.
        if (inkPath == null) {
            inkPath = new GeneralPath();
        }
        inkPath.moveTo(e.getX(), e.getY());
        pageViewComponent.repaint();
    }

    public void mouseReleased(MouseEvent e) {

        inkPath.moveTo(e.getX(), e.getY());

        // convert bbox and start and end line points.
        Rectangle bBox = inkPath.getBounds();
        // check to make sure the bbox isn't zero height or width
        bBox.setRect(bBox.getX() - 5, bBox.getY() - 5,
                bBox.getWidth() + 10, bBox.getHeight() + 10);

        Shape tInkPath = convertToPageSpace(inkPath);
        Rectangle tBbox = convertToPageSpace(bBox).getBounds();

        // create annotations types that that are rectangle based;
        // which is actually just link annotations
        InkAnnotation annotation = (InkAnnotation)
                AnnotationFactory.buildAnnotation(
                        documentViewModel.getDocument().getPageTree().getLibrary(),
                        Annotation.SUBTYPE_INK,
                        tBbox, null);

        annotation.setColor(lineColor);
        annotation.setBorderStyle(borderStyle);
        annotation.setInkPath(tInkPath);

        // pass outline shapes and bounds to create the highlight shapes
        annotation.setAppearanceStream(tBbox);

        // create the annotation object.
        AbstractAnnotationComponent comp =
                AnnotationComponentFactory.buildAnnotationComponent(
                        annotation,
                        documentViewController,
                        pageViewComponent, documentViewModel);
        // set the bounds and refresh the userSpace rectangle
        comp.setBounds(bBox);
        // resets user space rectangle to match bbox converted to page space
        comp.refreshAnnotationRect();

        // add them to the container, using absolute positioning.
        if (documentViewController.getAnnotationCallback() != null) {
            AnnotationCallback annotationCallback =
                    documentViewController.getAnnotationCallback();
            annotationCallback.newAnnotation(pageViewComponent, comp);
        }

        // set the annotation tool to he select tool
        documentViewController.getParentController().setDocumentToolMode(
                DocumentViewModel.DISPLAY_TOOL_SELECTION);

        // clear the path
        inkPath = null;
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mouseDragged(MouseEvent e) {
        inkPath.lineTo(e.getX(), e.getY());
        pageViewComponent.repaint();
    }

    public void mouseMoved(MouseEvent e) {

    }

    /**
     * Convert the shapes that make up the annotation to page space so that
     * they will scale correctly at different zooms.
     *
     * @return transformed bbox.
     */
    protected Shape convertToPageSpace(Shape shape) {
        Page currentPage = pageViewComponent.getPage();
        AffineTransform at = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        try {
            at = at.createInverse();
        } catch (NoninvertibleTransformException e1) {
            e1.printStackTrace();
        }

        shape = at.createTransformedShape(shape);

        return shape;

    }
}
