/*
 * $Id$
 *
 * Copyright 2004 Sun Microsystems, Inc., 4150 Network Circle,
 * Santa Clara, California 95054, U.S.A. All rights reserved.
 */
package org.jdesktop.swingx;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Collapses or expands its content area with animation and fade in/fade out
 * effects.
 * 
 * @javabean.attribute
 *          name="isContainer"
 *          value="Boolean.TRUE"
 *          rtexpr="true"
 * 
 * @javabean.attribute
 *          name="containerDelegate"
 *          value="getContentPane"
 *          
 * @javabean.class
 *          name="JXCollapsiblePane"
 *          shortDescription="A pane which hides its content with an animation."
 *          stopClass="java.awt.Component"
 *          
 * @author rbair (from the JDNC project)
 * @author <a href="mailto:fred@L2FProd.com">fred</a>
 */
public class JXCollapsiblePane extends JPanel {

  /**
   * Used when generating PropertyChangeEvents for the "animationState" property
   */
  public final static String ANIMATION_STATE_KEY = "animationState";
  
  /**
   * Indicates whether the component is collapsed or expanded
   */
  private boolean collapsed = false;

  /**
   * Timer used for doing the transparency animation (fade-in)
   */
  private Timer animateTimer;
  private AnimationListener animator;
  private int currentHeight = -1;
  private WrapperContainer wrapper;
  private boolean useAnimation = true;
  private AnimationParams animationParams;

  /**
   * Constructs a new JXCollapsiblePane with a {@link JPanel} as content pane and
   * a vertical {@link VerticalLayout} with a gap of 2 pixels as layout manager.
   */
  public JXCollapsiblePane() {
    super.setLayout(new BorderLayout(0, 0));

    JPanel panel = new JPanel();
    panel.setLayout(new VerticalLayout(2));
    setContentPane(panel);

    animator = new AnimationListener();
    setAnimationParams(new AnimationParams(30, 8, 0.01f, 1.0f));
  }

  /**
   * If true, enables the animation when pane is collapsed/expanded. If false,
   * animation is turned off.
   * 
   * @param animated
   * @javabean.property
   *          bound="true"
   *          preferred="true"
   */
  public void setAnimated(boolean animated) {
    if (animated != useAnimation) {
      useAnimation = animated;
      firePropertyChange("animated", !useAnimation, useAnimation);
    }
  }

  /**
   * @return true if the pane is animated, false otherwise
   */
  public boolean isAnimated() {
    return useAnimation;
  }

  /**
   * Sets the content pane of this JXCollapsiblePane. Components must be added to
   * this content pane, not to the JXCollapsiblePane.
   * 
   * @param contentPanel
   * @throws IllegalArgumentException if contentPanel is null
   */
  public void setContentPane(Container contentPanel) {
    if (contentPanel == null) {
      throw new IllegalArgumentException("Content pane can't be null");
    }
    
    if (wrapper != null) {
      super.remove(wrapper);
    }
    wrapper = new WrapperContainer(contentPanel);
    super.addImpl(wrapper, BorderLayout.CENTER, -1);
  }

  /**
   * @return the content pane
   */
  public Container getContentPane() {
    return wrapper.c;
  }

  /**
   * Overriden to redirect call to the content pane.
   */
  public void setLayout(LayoutManager mgr) {
    // wrapper can be null when setLayout is called by "super()" constructor
    if (wrapper != null) {
      getContentPane().setLayout(mgr);
    }
  }

  /**
   * Overriden to redirect call to the content pane.
   */
  protected void addImpl(Component comp, Object constraints, int index) {
    getContentPane().add(comp, constraints, index);
  }

  /**
   * Overriden to redirect call to the content pane
   */
  public void remove(Component comp) {
    getContentPane().remove(comp);
  }

  /**
   * Overriden to redirect call to the content pane.
   */
  public void remove(int index) {
    getContentPane().remove(index);
  }
  
  /**
   * Overriden to redirect call to the content pane.
   */
  public void removeAll() {
    getContentPane().removeAll();
  }
  
  /**
   * @return true if the pane is collapsed, false if expanded
   */
  public boolean isCollapsed() {
    return collapsed;
  }

  /**
   * If the component is collapsed and <code>val</code> is false, then this
   * call scrolls down the JXCollapsiblePane, such that the entire
   * JXCollapsiblePane will be visible. This also causes the content container to
   * be faded in. However, if the component is expanded and <code>val</code>
   * is true, then this call scrolls up the JXCollapsiblePane. Also, this will
   * cause the content container to be faded out. <b>Note: the animation occurs
   * only if {@link JXCollapsiblePane#isAnimated()} returns true</b>
   * 
   * @javabean.property
   *          bound="true"
   *          preferred="true"
   */
  public void setCollapsed(boolean val) {
    if (collapsed != val) {
      collapsed = val;
      if (isAnimated()) {
        if (collapsed) {
          setAnimationParams(new AnimationParams(30, Math.max(8, wrapper
            .getHeight() / 10), 1.0f, 0.01f));
          animator.reinit(wrapper.getHeight(), 0);
          animateTimer.start();
        } else {
          setAnimationParams(new AnimationParams(30, Math.max(8,
            getContentPane().getPreferredSize().height / 10), 0.01f, 1.0f));
          animator.reinit(wrapper.getHeight(), getContentPane()
            .getPreferredSize().height);
          animateTimer.start();
        }
      } else {
        wrapper.c.setVisible(!collapsed);
        invalidate();
        doLayout();
      }
      repaint();
      firePropertyChange("collapsed", !collapsed, collapsed);
    }
  }

  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.awt.Component#getPreferredSize()
   */
  public Dimension getPreferredSize() {
    /*
     * The preferred size is calculated based on the current position of the
     * component in its animation sequence. If the Component is expanded, then
     * the preferred size will be the preferred size of the top component plus
     * the preferred size of the embedded content container. <p>However, if the
     * scroll up is in any state of animation, the height component of the
     * preferred size will be the current height of the component (as contained
     * in the currentHeight variable)
     */
    if (!isAnimated()) {
      if (getContentPane().isVisible()) {
        return getContentPane().getPreferredSize();
      } else {
        return super.getPreferredSize();
      }
    } else {
      Dimension dim = new Dimension(getContentPane().getPreferredSize());
      if (!getContentPane().isVisible() && currentHeight != -1) {
        dim.height = currentHeight;
      }
      return dim;
    }
  }

  /**
   * Sets the parameters controlling the animation
   * 
   * @param params
   * @throws IllegalArgumentException
   *           if params is null
   */
  private void setAnimationParams(AnimationParams params) {
    if (params == null) { throw new IllegalArgumentException(
      "params can't be null"); }
    if (animateTimer != null) {
      animateTimer.stop();
    }
    animationParams = params;
    animateTimer = new Timer(animationParams.waitTime, animator);
    animateTimer.setInitialDelay(0);
  }
  
  /**
   * Tagging interface for containers in a JXCollapsiblePane hierarchy who needs
   * to be revalidated (invalidate/validate/repaint) when the pane is expanding
   * or collapsing. Usually validating only the parent of the JXCollapsiblePane
   * is enough but there might be cases where the parent parent must be
   * validated.
   */
  public static interface JCollapsiblePaneContainer {
    Container getValidatingContainer();
  }

  /**
   * Parameters controlling the animations
   */
  private static class AnimationParams {
    final int waitTime;
    final int deltaY;
    final float alphaStart;
    final float alphaEnd;

    /**
     * @param waitTime
     *          the amount of time in milliseconds to wait between calls to the
     *          animation thread
     * @param deltaY
     *          the delta in the Y direction to inc/dec the size of the scroll
     *          up by
     * @param alphaStart
     *          the starting alpha transparency level
     * @param alphaEnd
     *          the ending alpha transparency level
     */
    public AnimationParams(int waitTime, int deltaY, float alphaStart,
      float alphaEnd) {
      this.waitTime = waitTime;
      this.deltaY = deltaY;
      this.alphaStart = alphaStart;
      this.alphaEnd = alphaEnd;
    }
  }

  /**
   * This class actual provides the animation support for scrolling up/down this
   * component. This listener is called whenever the animateTimer fires off. It
   * fires off in response to scroll up/down requests. This listener is
   * responsible for modifying the size of the content container and causing it
   * to be repainted.
   * 
   * @author Richard Bair
   */
  private final class AnimationListener implements ActionListener {
    /**
     * Mutex used to ensure that the startHeight/finalHeight are not changed
     * during a repaint operation.
     */
    private final Object ANIMATION_MUTEX = "Animation Synchronization Mutex";
    /**
     * This is the starting height when animating. If > finalHeight, then the
     * animation is going to be to scroll up the component. If it is < then
     * finalHeight, then the animation will scroll down the component.
     */
    private int startHeight = 0;
    /**
     * This is the final height that the content container is going to be when
     * scrolling is finished.
     */
    private int finalHeight = 0;
    /**
     * The current alpha setting used during "animation" (fade-in/fade-out)
     */
    private float animateAlpha = 1.0f;

    public void actionPerformed(ActionEvent e) {
      /*
       * Pre-1) If startHeight == finalHeight, then we're done so stop the timer
       * 1) Calculate whether we're contracting or expanding. 2) Calculate the
       * delta (which is either positive or negative, depending on the results
       * of (1)) 3) Calculate the alpha value 4) Resize the ContentContainer 5)
       * Revalidate/Repaint the content container
       */
      synchronized (ANIMATION_MUTEX) {
        if (startHeight == finalHeight) {
          animateTimer.stop();
          animateAlpha = animationParams.alphaEnd;
          // keep the content pane hidden when it is collapsed, other it may
          // still receive focus.
          if (finalHeight > 0) {
            wrapper.showContent();   
            validate();
            JXCollapsiblePane.this.firePropertyChange(ANIMATION_STATE_KEY, null,
              "expanded");
            return;
          }
        }

        final boolean contracting = startHeight > finalHeight;
        final int delta_y = contracting?-1 * animationParams.deltaY
          :animationParams.deltaY;
        int newHeight = wrapper.getHeight() + delta_y;
        if (contracting) {
          newHeight = newHeight < finalHeight?finalHeight:newHeight;
        } else {
          newHeight = newHeight > finalHeight?finalHeight:newHeight;
        }
        animateAlpha = (float)newHeight
          / (float)wrapper.c.getPreferredSize().height;

        Rectangle bounds = wrapper.getBounds();
        int oldHeight = bounds.height;
        bounds.height = newHeight;
        wrapper.setBounds(bounds);
        bounds = getBounds();
        bounds.height = (bounds.height - oldHeight) + newHeight;
        currentHeight = bounds.height;
        setBounds(bounds);
        startHeight = newHeight;
        
        // it happens the animateAlpha goes over the alphaStart/alphaEnd range
        // this code ensures it stays in bounds. This behavior is seen when
        // component such as JTextComponents are used in the container.
        if (contracting) {
          // alphaStart > animateAlpha > alphaEnd
          if (animateAlpha < animationParams.alphaEnd) {
            animateAlpha = animationParams.alphaEnd;
          }
          if (animateAlpha > animationParams.alphaStart) {
            animateAlpha = animationParams.alphaStart;            
          }
        } else {
          // alphaStart < animateAlpha < alphaEnd
          if (animateAlpha > animationParams.alphaEnd) {
            animateAlpha = animationParams.alphaEnd;
          }
          if (animateAlpha < animationParams.alphaStart) {
            animateAlpha = animationParams.alphaStart;
          }
        }
        wrapper.alpha = animateAlpha;

        validate();
      }
    }
      
    void validate() {
      Container parent = SwingUtilities.getAncestorOfClass(
        JCollapsiblePaneContainer.class, JXCollapsiblePane.this);
      if (parent != null) {
        parent = ((JCollapsiblePaneContainer)parent).getValidatingContainer();
      } else {
        parent = getParent();
      }

      if (parent != null) {
        if (parent instanceof JComponent) {
          ((JComponent)parent).revalidate();
        } else {
          parent.invalidate();
        }
        parent.doLayout();
        parent.repaint();
      }        
    }

    /**
     * Reinitializes the timer for scrolling up/down the component. This method
     * is properly synchronized, so you may make this call regardless of whether
     * the timer is currently executing or not.
     * 
     * @param startHeight
     * @param stopHeight
     */
    public void reinit(int startHeight, int stopHeight) {
      synchronized (ANIMATION_MUTEX) {
        JXCollapsiblePane.this.firePropertyChange(ANIMATION_STATE_KEY, null,
          "reinit");
        this.startHeight = startHeight;
        this.finalHeight = stopHeight;
        animateAlpha = animationParams.alphaStart;
        currentHeight = -1;
        wrapper.showImage();
      }
    }
  }

  private final class WrapperContainer extends JPanel {
    private BufferedImage img;
    private Container c;
    float alpha = 1.0f;

    public WrapperContainer(Container c) {
      super(new BorderLayout());
      this.c = c;
      add(c, BorderLayout.CENTER);
      
      // we must ensure the container is opaque. It is not opaque it introduces
      // painting glitches specially on Linux with JDK 1.5 and GTK look and feel.
      // GTK look and feel calls setOpaque(false)
      if (c instanceof JComponent && !((JComponent)c).isOpaque()) {
        ((JComponent)c).setOpaque(true);
      }
    }

    public void showImage() {
      // render c into the img
      makeImage();
      c.setVisible(false);
    }

    public void showContent() {
      currentHeight = -1;
      c.setVisible(true);
    }

    void makeImage() {
      // if we have no image or if the image has changed      
      if (getGraphicsConfiguration() != null && getWidth() > 0) {
        Dimension dim = c.getPreferredSize();
        // width and height must be > 0 to be able to create an image
        if (dim.height > 0) {
          img = getGraphicsConfiguration().createCompatibleImage(getWidth(),
            dim.height);
          c.setSize(getWidth(), dim.height);
          c.paint(img.getGraphics());
        } else {
          img = null;
        }
      }
    }
    
    public void paintComponent(Graphics g) {
      if (!useAnimation || c.isVisible()) {
        super.paintComponent(g);
      } else {
        // within netbeans, it happens we arrive here and the image has not been
        // created yet. We ensure it is.
        if (img == null) {
          makeImage();
        }
        // and we paint it only if it has been created and only if we have a
        // valid graphics
        if (g != null && img != null) {
          // draw the image with y being height - imageHeight
          g.drawImage(img, 0, getHeight() - img.getHeight(), null);
        }
      }
    }

    public void paint(Graphics g) {
      Graphics2D g2d = (Graphics2D)g;
      Composite oldComp = g2d.getComposite();
      Composite alphaComp = AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
        alpha);
      g2d.setComposite(alphaComp);
      super.paint(g2d);
      g2d.setComposite(oldComp);
    }

  }
}
