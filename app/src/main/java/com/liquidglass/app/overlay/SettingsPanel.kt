package com.liquidglass.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.example.liquidglass.BlurMethod
import com.example.liquidglass.ChromaticAberrationEffect
import com.example.liquidglass.DisplacementMode
import com.example.liquidglass.GlassAccessibilityMode
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView
import com.liquidglass.app.R


class SettingsPanel(
    context: Context,
    private val glass: LiquidGlassView,
    private val onRequestClose: (() -> Unit)? = null
) : LinearLayout(context) {

    private val screenDensity = resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * screenDensity).toInt()
    private fun dpF(v: Int): Float = v * screenDensity

    private var aberrationGroup: LinearLayout? = null
    private var dispersionGroup: LinearLayout? = null
    private var lensGroup: LinearLayout? = null
    private var cpuOptionsGroup: LinearLayout? = null

    private val extraGlassViews = mutableListOf<LiquidGlassView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(COLOR_BG)
        setPadding(dp(16), dp(14), dp(16), dp(12))
    }

    
    fun build(): SettingsPanel {
        addView(TextView(context).apply {
            text = context.getString(R.string.debug_panel_title)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT)
            setPadding(dp(6), 0, 0, dp(4))
        })

        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, dp(24))
        }
        buildPanel(content)
        addView(content)
        return this
    }

    
    private fun applyGlass(block: (LiquidGlassView) -> Unit) {
        block(glass)
        extraGlassViews.forEach(block)
    }

    private fun buildPanel(root: LinearLayout) {
        
        val perfCard = addCard(root, context.getString(R.string.section_performance))
        addSwitchRow(perfCard, context.getString(R.string.switch_performance_overlay), true) {  }

        
        val pathCard = addCard(root, context.getString(R.string.section_render_path))
        val initialPath = when {
            !glass.useHardwareBlurWhenPossible -> 2
            !glass.useShaderPipeline -> 1
            else -> 0
        }
        addSegmented(
            pathCard,
            listOf(
                context.getString(R.string.render_lens),
                context.getString(R.string.render_classic_gpu),
                context.getString(R.string.render_force_cpu)
            ),
            initialPath
        ) { index ->
            applyGlass {
                it.useHardwareBlurWhenPossible = index != 2
                it.useShaderPipeline = index == 0
            }
            lensGroup?.visibility = if (index == 0) View.VISIBLE else View.GONE
            cpuOptionsGroup?.visibility = if (index == 2) View.VISIBLE else View.GONE
        }
        addNote(pathCard, context.getString(R.string.gpu_render_desc))

        addLabel(pathCard, context.getString(R.string.simulate_api_label))
        addSegmented(
            pathCard,
            listOf(context.getString(R.string.api_cap_device), "33–35", "31–32", "≤ 30"),
            0
        ) { index ->
            val cap = when (index) {
                1 -> 35
                2 -> 32
                3 -> 30
                else -> Int.MAX_VALUE
            }
            applyGlass { it.debugApiLevelCap = cap }
        }
        addNote(pathCard, context.getString(R.string.simulate_api_desc))

        lensGroup = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = if (initialPath == 0) View.VISIBLE else View.GONE
        }
        pathCard.addView(lensGroup)
        buildLensOptions(lensGroup!!)

        cpuOptionsGroup = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = if (initialPath == 2) View.VISIBLE else View.GONE
        }
        pathCard.addView(cpuOptionsGroup)
        buildCpuOptions(cpuOptionsGroup!!)

        
        val glassCard = addCard(root, context.getString(R.string.section_glass_params))

        addSwitchRow(glassCard, context.getString(R.string.switch_enable_blur), glass.enableBackdropBlur) { checked ->
            applyGlass { it.enableBackdropBlur = checked }
        }
        addSlider(glassCard, 100, (glass.blurAmount * 100).toInt(),
            { context.getString(R.string.blur_amount, it / 100f) }) { p ->
            applyGlass { it.blurAmount = p / 100f }
        }
        addSlider(glassCard, 100, (glass.saturation - 100).toInt(),
            { context.getString(R.string.saturation_value, it + 100) }) { p ->
            applyGlass { it.saturation = (p + 100).toFloat() }
        }
        addSlider(glassCard, 999, glass.cornerRadius.toInt(),
            { context.getString(R.string.corner_radius_value, it.toFloat()) }) { p ->
            glass.cornerRadius = p.toFloat()
        }
        addSwitchRow(glassCard, context.getString(R.string.switch_over_light), glass.overLight) { checked ->
            applyGlass { it.overLight = checked }
        }
        addSwitchRow(glassCard, context.getString(R.string.switch_enable_edge_highlight), glass.enableEdgeHighlight) { checked ->
            applyGlass { it.enableEdgeHighlight = checked }
        }
        addSlider(glassCard, 100, ((glass.edgeHighlightBorderWidth - 0.5f) / 4.5f * 100).toInt(),
            { context.getString(R.string.border_width, 0.5f + it / 100f * 4.5f) }) { p ->
            applyGlass { it.edgeHighlightBorderWidth = 0.5f + p / 100f * 4.5f }
        }
        addSlider(glassCard, 100, glass.edgeHighlightOpacity.toInt(),
            { context.getString(R.string.highlight_opacity, it.toFloat()) }) { p ->
            applyGlass { it.edgeHighlightOpacity = p.toFloat() }
        }

        
        buildTintCard(root)

        
        val interactionCard = addCard(root, context.getString(R.string.section_interaction))
        addSwitchRow(interactionCard, context.getString(R.string.switch_press_effect), glass.enablePressEffect) { checked ->
            applyGlass { it.enablePressEffect = checked }
        }
        addSlider(interactionCard, 40, ((glass.pressScale - 0.8f) * 100f).toInt(),
            { context.getString(R.string.press_scale_value, 0.8f + it / 100f) }) { p ->
            applyGlass { it.pressScale = 0.8f + p / 100f }
        }
        addSlider(interactionCard, 50, (glass.elasticity * 100).toInt(),
            { context.getString(R.string.elasticity_value, it / 100f) }) { p ->
            applyGlass { it.elasticity = p / 100f }
        }

        
        val colorCard = addCard(root, context.getString(R.string.section_color_effect))
        val initialEffect = when {
            glass.enableChromaticDispersion -> 2
            glass.enableChromaticAberration -> 1
            else -> 0
        }
        addSegmented(
            colorCard,
            listOf(
                context.getString(R.string.effect_none),
                context.getString(R.string.algorithm_aberration),
                context.getString(R.string.algorithm_dispersion)
            ),
            initialEffect
        ) { index ->
            when (index) {
                0 -> applyGlass { it.enableChromaticDispersion = false; it.enableChromaticAberration = false }
                1 -> applyGlass { it.enableChromaticDispersion = false; it.enableChromaticAberration = true }
                2 -> applyGlass { it.enableChromaticAberration = false; it.enableChromaticDispersion = true }
            }
            aberrationGroup?.visibility = if (index == 1) View.VISIBLE else View.GONE
            dispersionGroup?.visibility = if (index == 2) View.VISIBLE else View.GONE
        }

        aberrationGroup = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = if (initialEffect == 1) View.VISIBLE else View.GONE
        }
        colorCard.addView(aberrationGroup)
        buildAberrationOptions(aberrationGroup!!)

        dispersionGroup = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = if (initialEffect == 2) View.VISIBLE else View.GONE
        }
        colorCard.addView(dispersionGroup)
        buildDispersionOptions(dispersionGroup!!)

        
        val miscCard = addCard(root, context.getString(R.string.section_background_language))
        addButton(miscCard, context.getString(R.string.button_change_background)) {
            onRequestClose?.invoke()
        }
    }

    
    private fun buildLensOptions(group: LinearLayout) {
        addNote(group, context.getString(R.string.lens_note))

        addLabel(group, context.getString(R.string.lens_material))
        addSegmented(
            group,
            listOf(context.getString(R.string.material_regular), context.getString(R.string.material_clear)),
            if (glass.material == GlassMaterial.CLEAR) 1 else 0
        ) { index ->
            applyGlass { it.material = if (index == 1) GlassMaterial.CLEAR else GlassMaterial.REGULAR }
        }

        addSwitchRow(group, context.getString(R.string.switch_sensor_highlight), glass.enableSensorHighlight) { checked ->
            applyGlass { it.enableSensorHighlight = checked }
        }
        addSwitchRow(group, context.getString(R.string.switch_adaptive_tint), glass.enableAdaptiveTint) { checked ->
            applyGlass { it.enableAdaptiveTint = checked }
        }
        addSwitchRow(group, context.getString(R.string.switch_refraction_no_fold), glass.refractionNoFold) { checked ->
            applyGlass { it.refractionNoFold = checked }
        }
        addSwitchRow(group, context.getString(R.string.switch_refraction_outward), glass.refractionOutward) { checked ->
            applyGlass { it.refractionOutward = checked }
        }
        addSwitchRow(group, context.getString(R.string.switch_adaptive_lens_scale), glass.adaptiveLensScale) { checked ->
            applyGlass { it.adaptiveLensScale = checked }
        }

        addSlider(group, 118, (glass.bevelWidth - 2f).toInt(),
            { context.getString(R.string.lens_bevel, it + 2f) }) { p ->
            applyGlass { it.bevelWidth = p + 2f }
        }
        addSlider(group, 200, glass.refractionHeight.toInt(),
            { context.getString(R.string.lens_refraction, it.toFloat()) }) { p ->
            applyGlass { it.refractionHeight = p.toFloat() }
        }
        addSlider(group, 40, (glass.refractionFalloff * 10f).toInt(),
            { context.getString(R.string.lens_falloff, it / 10f) }) { p ->
            applyGlass { it.refractionFalloff = p / 10f }
        }
        addSlider(group, 100, (glass.dispersionStrength * 100f).toInt(),
            { context.getString(R.string.lens_dispersion, it / 100f) }) { p ->
            applyGlass { it.dispersionStrength = p / 100f }
        }

        addSwitchRow(
            group,
            context.getString(R.string.switch_a11y_opaque),
            glass.accessibilityMode == GlassAccessibilityMode.FORCE_OPAQUE
        ) { checked ->
            applyGlass {
                it.accessibilityMode = if (checked) GlassAccessibilityMode.FORCE_OPAQUE else GlassAccessibilityMode.AUTO
            }
        }
    }

    private fun buildCpuOptions(group: LinearLayout) {
        addNote(group, context.getString(R.string.section_cpu_options))

        val methods = listOf(
            context.getString(R.string.blur_method_smart) to BlurMethod.SMART,
            context.getString(R.string.blur_method_box) to BlurMethod.BOX_BLUR,
            context.getString(R.string.blur_method_box_cpp) to BlurMethod.BOX_BLUR_CPP,
            context.getString(R.string.blur_method_iir) to BlurMethod.IIR_GAUSSIAN,
            context.getString(R.string.blur_method_neon) to BlurMethod.IIR_GAUSSIAN_NEON,
            context.getString(R.string.blur_method_box3) to BlurMethod.BOX3,
            context.getString(R.string.blur_method_downsample) to BlurMethod.DOWNSAMPLE
        )
        addLabel(group, context.getString(R.string.section_blur_method))
        val spinner = Spinner(context)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, methods.map { it.first })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(methods.indexOfFirst { it.second == glass.blurMethod }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyGlass { it.blurMethod = methods[position].second }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        group.addView(spinner)

        addLabel(group, context.getString(R.string.section_aberration_method))
        val modes = listOf(
            ChromaticAberrationEffect.PerformanceMode.AUTO,
            ChromaticAberrationEffect.PerformanceMode.CPP,
            ChromaticAberrationEffect.PerformanceMode.KOTLIN
        )
        addSegmented(
            group,
            listOf(context.getString(R.string.impl_auto), "C++", "Kotlin"),
            modes.indexOf(glass.chromaticAberrationMode).coerceAtLeast(0)
        ) { index ->
            applyGlass { it.chromaticAberrationMode = modes[index] }
        }

        addSwitchRow(group, context.getString(R.string.switch_bilinear_interpolation), glass.aberrationUseBilinearInterpolation) { checked ->
            applyGlass { it.aberrationUseBilinearInterpolation = checked }
        }
        addSwitchRow(group, context.getString(R.string.switch_high_quality), glass.highQualityBlur) { checked ->
            applyGlass { it.highQualityBlur = checked }
        }
        addSwitchRow(group, context.getString(R.string.switch_enable_optimized_capture), glass.enableOptimizedCapture) { checked ->
            applyGlass { it.enableOptimizedCapture = checked }
        }

        addSlider(group, 100, ((glass.globalDownsampleFactor - 0.25f) / 0.75f * 100).toInt(),
            { context.getString(R.string.global_downsample_value, 0.25f + it / 100f * 0.75f) }) { p ->
            applyGlass { it.globalDownsampleFactor = 0.25f + p / 100f * 0.75f }
        }
        addSlider(group, 100, ((glass.aberrationDownsample - 0.25f) / 0.75f * 100).toInt(),
            { context.getString(R.string.aberration_downsample_value, 0.25f + it / 100f * 0.75f) }) { p ->
            applyGlass { it.aberrationDownsample = 0.25f + p / 100f * 0.75f }
        }
    }

    private fun buildAberrationOptions(group: LinearLayout) {
        addSlider(group, 100, (glass.aberrationIntensity * 10).toInt(),
            { context.getString(R.string.aberration_value, it / 10f) }) { p ->
            applyGlass { it.aberrationIntensity = p / 10f }
        }
        addSlider(group, 200, glass.displacementScale.toInt(),
            { context.getString(R.string.displacement_scale_value, it.toFloat()) }) { p ->
            applyGlass { it.displacementScale = p.toFloat() }
        }
        addLabel(group, context.getString(R.string.displacement_mode))
        val dispModes = listOf(DisplacementMode.STANDARD, DisplacementMode.POLAR, DisplacementMode.PROMINENT)
        addSegmented(
            group,
            listOf(
                context.getString(R.string.disp_mode_standard),
                context.getString(R.string.disp_mode_polar),
                context.getString(R.string.disp_mode_prominent)
            ),
            dispModes.indexOf(glass.displacementMode).coerceAtLeast(0)
        ) { index ->
            applyGlass { it.displacementMode = dispModes[index] }
        }

        addLabel(group, context.getString(R.string.channel_offset_advanced))
        addSlider(group, 200, (glass.aberrationRedOffset * 500 + 100).toInt(),
            { context.getString(R.string.red_offset, (it - 100) / 500f) }) { p ->
            applyGlass { it.aberrationRedOffset = (p - 100) / 500f }
        }
        addSlider(group, 200, (glass.aberrationGreenOffset * 500 + 100).toInt(),
            { context.getString(R.string.green_offset, (it - 100) / 500f) }) { p ->
            applyGlass { it.aberrationGreenOffset = (p - 100) / 500f }
        }
        addSlider(group, 200, (glass.aberrationBlueOffset * 500 + 100).toInt(),
            { context.getString(R.string.blue_offset, (it - 100) / 500f) }) { p ->
            applyGlass { it.aberrationBlueOffset = (p - 100) / 500f }
        }
    }

    private fun buildDispersionOptions(group: LinearLayout) {
        addNote(group, context.getString(R.string.dispersion_cpu_only_note))

        addLabel(group, context.getString(R.string.dispersion_preset))
        val presets = listOf(
            Triple(context.getString(R.string.preset_glass), 100f, 1.5f to 7f),
            Triple(context.getString(R.string.preset_diamond), 80f, 2.4f to 15f),
            Triple(context.getString(R.string.preset_crystal), 120f, 1.8f to 10f),
            Triple(context.getString(R.string.preset_rainbow), 150f, 1.3f to 20f),
            Triple(context.getString(R.string.preset_subtle), 200f, 1.2f to 3f)
        )

        lateinit var updateSliders: () -> Unit

        val spinner = Spinner(context)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, presets.map { it.first })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val (_, thickness, rest) = presets[position]
                val (factor, gain) = rest
                applyGlass {
                    it.dispersionThickness = thickness
                    it.dispersionFactor = factor
                    it.dispersionGain = gain
                }
                updateSliders()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        group.addView(spinner)

        val (tvThickness, seekThickness) = addSlider(group, 150, (glass.dispersionThickness - 50).toInt(),
            { context.getString(R.string.dispersion_thickness, it + 50f) }) { p ->
            applyGlass { it.dispersionThickness = p + 50f }
        }
        val (tvFactor, seekFactor) = addSlider(group, 100, ((glass.dispersionFactor - 1f) * 50).toInt(),
            { context.getString(R.string.dispersion_factor, 1f + it / 50f) }) { p ->
            applyGlass { it.dispersionFactor = 1f + p / 50f }
        }
        val (tvGain, seekGain) = addSlider(group, 50, glass.dispersionGain.toInt(),
            { context.getString(R.string.dispersion_gain, it.toFloat()) }) { p ->
            applyGlass { it.dispersionGain = p.toFloat() }
        }
        addSlider(group, 100, ((glass.dispersionDownsample - 0.25f) / 0.75f * 100).toInt(),
            { context.getString(R.string.dispersion_downscale, 0.25f + it / 100f * 0.75f) }) { p ->
            applyGlass { it.dispersionDownsample = 0.25f + p / 100f * 0.75f }
        }

        updateSliders = {
            seekThickness.progress = (glass.dispersionThickness - 50).toInt()
            tvThickness.text = context.getString(R.string.dispersion_thickness, glass.dispersionThickness)
            seekFactor.progress = ((glass.dispersionFactor - 1f) * 50).toInt()
            tvFactor.text = context.getString(R.string.dispersion_factor, glass.dispersionFactor)
            seekGain.progress = glass.dispersionGain.toInt()
            tvGain.text = context.getString(R.string.dispersion_gain, glass.dispersionGain)
        }
    }

    

    private fun addCard(parent: LinearLayout, title: String): LinearLayout {
        parent.addView(TextView(context).apply {
            text = title
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT_DIM)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18); bottomMargin = dp(6); marginStart = dp(8)
            }
        })
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpF(14)
                setColor(COLOR_CARD)
            }
            setPadding(dp(14), dp(6), dp(14), dp(12))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        parent.addView(card)
        return card
    }

    private fun addLabel(parent: LinearLayout, text: String) {
        parent.addView(TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(COLOR_TEXT)
            setPadding(0, dp(12), 0, dp(4))
        })
    }

    private fun addNote(parent: LinearLayout, text: String) {
        parent.addView(TextView(context).apply {
            this.text = text
            textSize = 11f
            setTextColor(COLOR_TEXT_DIM)
            setPadding(0, dp(6), 0, dp(4))
        })
    }

    private fun addButton(parent: LinearLayout, text: String, onClick: () -> Unit) {
        parent.addView(Button(context).apply {
            this.text = text
            isAllCaps = false
            setOnClickListener { onClick() }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        })
    }

    private fun addSwitchRow(parent: LinearLayout, label: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(COLOR_TEXT)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        val sw = Switch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        row.addView(sw)
        parent.addView(row)
        return sw
    }

    private fun addSlider(
        parent: LinearLayout,
        max: Int,
        initial: Int,
        format: (Int) -> String,
        onChange: (Int) -> Unit
    ): Pair<TextView, SeekBar> {
        val clamped = initial.coerceIn(0, max)
        val tv = TextView(context).apply {
            text = format(clamped)
            textSize = 13f
            setTextColor(COLOR_TEXT)
            setPadding(0, dp(8), 0, 0)
        }
        val seek = SeekBar(context).apply {
            this.max = max
            progress = clamped
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    tv.text = format(p)
                    if (fromUser) onChange(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        parent.addView(tv)
        parent.addView(seek)
        return tv to seek
    }

    
    private fun addSegmented(parent: LinearLayout, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8); bottomMargin = dp(4)
            }
            background = GradientDrawable().apply {
                cornerRadius = dpF(10)
                setColor(COLOR_SEG_BG)
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }

        val labels = mutableListOf<TextView>()
        var current = selectedIndex.coerceIn(0, options.size - 1)

        fun render() {
            labels.forEachIndexed { i, tv ->
                if (i == current) {
                    tv.background = GradientDrawable().apply {
                        cornerRadius = dpF(8)
                        setColor(Color.WHITE)
                    }
                    tv.setTextColor(COLOR_TEXT)
                    tv.typeface = Typeface.DEFAULT_BOLD
                } else {
                    tv.background = null
                    tv.setTextColor(0xFF666666.toInt())
                    tv.typeface = Typeface.DEFAULT
                }
            }
        }

        options.forEachIndexed { i, label ->
            val tv = TextView(context).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, dp(7))
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    if (current != i) {
                        current = i
                        render()
                        onSelect(i)
                    }
                }
            }
            labels += tv
            row.addView(tv)
        }
        render()
        parent.addView(row)
    }

    private fun buildTintCard(root: LinearLayout) {
        val card = addCard(root, context.getString(R.string.section_glass_tint))

        val current = glass.glassTint
        var hueRes = R.string.tint_none
        var hue = if (Color.alpha(current) == 0) Color.TRANSPARENT else current or 0xFF000000.toInt()
        var strength = if (Color.alpha(current) == 0) 0.30f else Color.alpha(current) / 255f
        TINT_SWATCHES.firstOrNull { it.second == hue }?.let { hueRes = it.first }

        val readout = TextView(context).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_TEXT)
            setPadding(0, dp(8), 0, 0)
        }
        fun refreshReadout() {
            readout.text = if (hue == Color.TRANSPARENT) {
                context.getString(R.string.tint_readout_none)
            } else {
                context.getString(
                    R.string.tint_readout,
                    context.getString(hueRes),
                    "#%06X".format(hue and 0xFFFFFF),
                    (strength * 100).toInt()
                )
            }
        }
        fun applyTint() {
            applyGlass {
                if (hue == Color.TRANSPARENT) it.glassTint = Color.TRANSPARENT
                else it.setGlassTint(hue, strength)
            }
            refreshReadout()
        }

        val (swatchRow, selectSwatch) = buildTintSwatchRow { labelRes, color ->
            hueRes = labelRes
            hue = color
            applyTint()
        }
        selectSwatch(hue)

        card.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(swatchRow)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        })
        card.addView(readout)
        card.addView(SeekBar(context).apply {
            max = 100
            progress = (strength * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    strength = p / 100f
                    if (hue == Color.TRANSPARENT) {
                        hueRes = R.string.tint_blue
                        hue = 0xFF0A84FF.toInt()
                        selectSwatch(hue)
                    }
                    applyTint()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        refreshReadout()
    }

    private fun buildTintSwatchRow(onPick: (labelRes: Int, color: Int) -> Unit): Pair<View, (Int) -> Unit> {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dots = mutableListOf<View>()
        var selected = 0

        fun render() {
            dots.forEachIndexed { i, dot ->
                val color = TINT_SWATCHES[i].second
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (color == Color.TRANSPARENT) COLOR_SEG_BG else color)
                    setStroke(
                        dp(if (i == selected) 3 else 1),
                        if (i == selected) COLOR_ACCENT else 0x33000000
                    )
                }
            }
        }

        TINT_SWATCHES.forEachIndexed { i, (labelRes, color) ->
            val dot = View(context).apply {
                layoutParams = LayoutParams(dp(32), dp(32)).apply { setMargins(0, 0, dp(10), 0) }
                setOnClickListener {
                    selected = i
                    render()
                    onPick(labelRes, color)
                }
            }
            dots += dot
            row.addView(dot)
        }
        render()

        return row to { color: Int ->
            val idx = TINT_SWATCHES.indexOfFirst { it.second == color }
            if (idx >= 0 && idx != selected) {
                selected = idx
                render()
            }
        }
    }

    companion object {
        private const val COLOR_BG = 0xFFF2F2F7.toInt()
        private const val COLOR_CARD = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT = 0xFF111111.toInt()
        private const val COLOR_TEXT_DIM = 0xFF8E8E93.toInt()
        private const val COLOR_ACCENT = 0xFF007AFF.toInt()
        private const val COLOR_SEG_BG = 0xFFE9E9EB.toInt()

        private val TINT_SWATCHES = listOf(
            R.string.tint_none to Color.TRANSPARENT,
            R.string.tint_blue to 0xFF0A84FF.toInt(),
            R.string.tint_cyan to 0xFF32ADE6.toInt(),
            R.string.tint_green to 0xFF30D158.toInt(),
            R.string.tint_yellow to 0xFFFFD60A.toInt(),
            R.string.tint_orange to 0xFFFF9F0A.toInt(),
            R.string.tint_red to 0xFFFF453A.toInt(),
            R.string.tint_pink to 0xFFFF375F.toInt(),
            R.string.tint_purple to 0xFFBF5AF2.toInt()
        )
    }
}