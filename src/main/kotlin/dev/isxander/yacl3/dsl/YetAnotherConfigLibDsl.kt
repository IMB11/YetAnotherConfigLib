package dev.isxander.yacl3.dsl

import dev.isxander.yacl3.api.*
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component

fun YetAnotherConfigLib(namespace: String, block: YACLDsl.() -> Unit): YetAnotherConfigLib {
    val context = YACLDslContext(namespace)
    context.block()
    return context.build()
}

class YACLDslContext(
    private val namespace: String,
    private val builder: YetAnotherConfigLib.Builder = YetAnotherConfigLib.createBuilder()
) : YACLDsl {
    private val categoryMap = LinkedHashMap<String, YACLDslCategoryContext>()
    private val categoryDslReferenceMap = mutableMapOf<String, FutureValue.Impl<CategoryDslReference>>()

    override val namespaceKey = "yacl3.config.$namespace"

    private var used = false
    private var built: YetAnotherConfigLib? = null

    private var saveFunction: () -> Unit = {}

    override val categories = object : YACLDslReference {
        override fun get(): YetAnotherConfigLib? = built

        override operator fun get(id: String): FutureValue<CategoryDslReference> =
            FutureValue.Impl(categoryMap[id]?.groups).also { categoryDslReferenceMap[id] = it }

        override fun registering(block: CategoryDsl.() -> Unit): RegisterableDelegateProvider<CategoryDsl, ConfigCategory> {
            return RegisterableDelegateProvider({ id, configuration -> category(id, configuration) }, block)
        }

        override val isBuilt: Boolean
            get() = built != null
    }

    init {
        title(Component.translatable("$namespaceKey.title"))
    }

    override fun title(component: Component) {
        builder.title(component)
    }

    override fun title(block: () -> Component) {
        title(block())
    }

    override fun category(id: String, block: CategoryDsl.() -> Unit): ConfigCategory {
        val context = YACLDslCategoryContext(id, this)
        context.block()
        categoryMap[id] = context
        categoryDslReferenceMap[id]?.value = context.groups

        val built = context.build()
        builder.category(built)

        return built
    }

    override fun save(block: () -> Unit) {
        val oldSaveFunction = saveFunction
        saveFunction = { // allows stacking of save functions
            oldSaveFunction()
            block()
        }
    }

    fun build(): YetAnotherConfigLib {
        if (used) error("Cannot use the same DSL context twice!")
        used = true

        builder.save(saveFunction)

        return builder.build().also { built = it }
    }
}

class YACLDslCategoryContext(
    private val id: String,
    private val root: YACLDslContext,
    private val builder: ConfigCategory.Builder = ConfigCategory.createBuilder(),
) : CategoryDsl {
    private val groupMap = LinkedHashMap<String, YACLDslGroupContext>()
    private val groupDslReferenceMap = mutableMapOf<String, FutureValue.Impl<GroupDslReference>>()
    val categoryKey = "${root.namespaceKey}.$id"

    private var built: ConfigCategory? = null

    private val rootGroup: YACLDslGroupContext = YACLDslGroupContext(id, this, builder.rootGroupBuilder(), root = true)

    override val groups = object : CategoryDslReference {
        override fun get(): ConfigCategory? = built

        override fun get(id: String): FutureValue<GroupDslReference> =
            FutureValue.Impl(groupMap[id]?.options).also { groupDslReferenceMap[id] = it }

        override val root: GroupDslReference
            get() = rootGroup.options

        override fun registering(block: GroupDsl.() -> Unit): RegisterableDelegateProvider<GroupDsl, OptionGroup> {
            return RegisterableDelegateProvider({ id, configuration -> group(id, configuration) }, block)
        }

        override val isBuilt: Boolean
            get() = built != null

    }

    override val options = rootGroup.options

    init {
        builder.name(Component.translatable("$categoryKey.title"))
    }

    override fun name(component: Component) {
        builder.name(component)
    }

    override fun name(block: () -> Component) {
        name(block())
    }

    override fun group(id: String, block: GroupDsl.() -> Unit): OptionGroup {
        val context = YACLDslGroupContext(id, this)
        context.block()
        groupMap[id] = context
        groupDslReferenceMap[id]?.value = context.options

        return context.build().also {
            builder.group(it)
        }
    }

    override fun <T : Any> option(id: String, block: OptionDsl<T>.() -> Unit): Option<T> =
        rootGroup.option(id, block)

    override fun tooltip(vararg component: Component) {
        builder.tooltip(*component)
    }

    override fun tooltipBuilder(block: TooltipBuilderDsl.() -> Unit) {
        val builder = TooltipBuilderDsl.Delegate { builder.tooltip(it) }
        builder.block()
    }

    override fun useDefaultTooltip(lines: Int) {
        if (lines == 1) {
            builder.tooltip(Component.translatable("$categoryKey.tooltip"))
        } else for (i in 1..lines) {
            builder.tooltip(Component.translatable("$categoryKey.tooltip.$i"))
        }
    }

    fun build(): ConfigCategory {
        return builder.build().also { built = it }
    }
}

class YACLDslGroupContext(
    private val id: String,
    private val category: YACLDslCategoryContext,
    private val builder: OptionGroup.Builder = OptionGroup.createBuilder(),
    private val root: Boolean = false,
) : GroupDsl {
    private val optionMap = LinkedHashMap<String, YACLDslOptionContext<*>>()
    private val optionDslReferenceMap = mutableMapOf<String, FutureValue.Impl<Option<*>>>()
    val groupKey = "${category.categoryKey}.$id"
    private var built: OptionGroup? = null

    override val options = object : GroupDslReference {
        override fun get(): OptionGroup? = built

        override fun <T> get(id: String): FutureValue<Option<T>> =
            FutureValue.Impl(optionMap[id]).flatMap { it.option as FutureValue<Option<T>> }.also { optionDslReferenceMap[id] = it as FutureValue.Impl<Option<*>> }

        override fun <T : Any> registering(block: OptionDsl<T>.() -> Unit): RegisterableDelegateProvider<OptionDsl<T>, Option<T>> {
            return RegisterableDelegateProvider({ id, configuration -> option(id, configuration) }, block)
        }

        override val isBuilt: Boolean
            get() = built != null

    }

    override fun name(component: Component) {
        builder.name(component)
    }

    override fun name(block: () -> Component) {
        name(block())
    }

    override fun descriptionBuilder(block: OptionDescription.Builder.() -> Unit) {
        builder.description(OptionDescription.createBuilder().apply(block).build())
    }

    override fun description(description: OptionDescription) {
        builder.description(description)
    }

    init {
        if (!root) {
            builder.name(Component.translatable("$groupKey.name"))
        }
    }

    override fun <T : Any> option(id: String, block: OptionDsl<T>.() -> Unit): Option<T> {
        val context = YACLDslOptionContext<T>(id, this)
        context.block()
        optionMap[id] = context

        return context.build().also {
            optionDslReferenceMap[id]?.value = it
            builder.option(it)
        }
    }

    override fun useDefaultDescription(lines: Int) {
        descriptionBuilder {
            if (lines == 1) {
                text(Component.translatable("$groupKey.description"))
            } else for (i in 1..lines) {
                text(Component.translatable("$groupKey.description.$i"))
            }
        }
    }

    fun build(): OptionGroup {
        return builder.build().also { built = it }
    }
}

class YACLDslOptionContext<T : Any>(
    private val id: String,
    private val group: YACLDslGroupContext,
    private val builder: Option.Builder<T> = Option.createBuilder()
) : Option.Builder<T> by builder, OptionDsl<T> {
    val optionKey = "${group.groupKey}.$id"
    private var built: Option<T>? = null

    private val taskQueue = ArrayDeque<(Option<T>) -> Unit>()
    override val option = FutureValue.Impl<Option<T>>()

    init {
        name(Component.translatable("$optionKey.name"))
    }

    override fun OptionDescription.Builder.addDefaultDescription(lines: Int?) {
        if (lines != null) {
            if (lines == 1) {
                text(Component.translatable("$optionKey.description"))
            } else for (i in 1..lines) {
                text(Component.translatable("$optionKey.description.$i"))
            }
        } else {
            // loop until we find a key that doesn't exist
            var i = 1
            while (i < 100) {
                val key = "$optionKey.description.$i"
                if (Language.getInstance().has(key)) {
                    text(Component.translatable(key))
                }

                i++
            }
        }
    }

    override fun build(): Option<T> {
        return builder.build().also {
            built = it
            option.value = it
            while (taskQueue.isNotEmpty()) {
                taskQueue.removeFirst()(it)
            }
        }
    }
}