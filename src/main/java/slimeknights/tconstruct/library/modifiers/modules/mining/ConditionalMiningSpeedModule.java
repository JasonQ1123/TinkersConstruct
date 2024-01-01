package slimeknights.tconstruct.library.modifiers.modules.mining;

import com.google.gson.JsonObject;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import slimeknights.mantle.data.GenericLoaderRegistry.IGenericLoader;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.block.BlockPredicate;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;
import slimeknights.tconstruct.library.json.math.ModifierFormula;
import slimeknights.tconstruct.library.json.math.ModifierFormula.FallbackFormula;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHook;
import slimeknights.tconstruct.library.modifiers.TinkerHooks;
import slimeknights.tconstruct.library.modifiers.hook.mining.BreakSpeedModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModuleCondition;
import slimeknights.tconstruct.library.modifiers.modules.behavior.ConditionalStatTooltip;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.stat.INumericToolStat;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import javax.annotation.Nullable;
import java.util.List;

import static slimeknights.tconstruct.library.json.math.ModifierFormula.FallbackFormula.BOOST;
import static slimeknights.tconstruct.library.json.math.ModifierFormula.FallbackFormula.PERCENT;

/**
 * Implementation of attack damage conditioned on the attacker or target's properties
 * @param block      Blocks to boost speed
 * @param holder     Condition on the entity holding this tool
 * @param formula    Damage formula
 * @param percent    If true, formula acts as a percent (try to display as a percent)
 * @param condition  Standard modifier conditions
 */
public record ConditionalMiningSpeedModule(IJsonPredicate<BlockState> block, IJsonPredicate<LivingEntity> holder, boolean requireEffective, ModifierFormula formula, boolean percent, ModifierModuleCondition condition) implements BreakSpeedModifierHook, ConditionalStatTooltip, ModifierModule {
  private static final List<ModifierHook<?>> DEFAULT_HOOKS = List.of(TinkerHooks.BREAK_SPEED, TinkerHooks.TOOLTIP);
  /** Variables for the modifier formula */
  private static final String[] VARIABLES = { "level", "speed", "multiplier", "original_speed" };
  /** Speed after modifiers ran */
  public static final int NEW_SPEED = 1;
  /** Mining speed multiplier */
  public static final int MULTIPLIER = 2;
  /** Speed before event listeners ran */
  public static final int ORIGINAL_SPEED = 3;

  @Nullable
  @Override
  public Integer getPriority() {
    // run multipliers a bit later
    return percent ? 75 : null;
  }

  @Override
  public void onBreakSpeed(IToolStackView tool, ModifierEntry modifier, BreakSpeed event, Direction sideHit, boolean isEffective, float miningSpeedModifier) {
    if ((isEffective || !requireEffective) && condition.matches(tool, modifier) && block.matches(event.getState())) {
      event.setNewSpeed(formula.apply(formula.computeLevel(tool, modifier), event.getNewSpeed(), tool.getMultiplier(ToolStats.MINING_SPEED) * miningSpeedModifier, event.getOriginalSpeed()));
    }
  }

  @Override
  public INumericToolStat<?> stat() {
    return ToolStats.MINING_SPEED;
  }

  @Override
  public float computeTooltipValue(IToolStackView tool, ModifierEntry entry) {
    return formula.apply(formula.computeLevel(tool, entry), 1, tool.getMultiplier(ToolStats.MINING_SPEED), 1);
  }

  @Override
  public List<ModifierHook<?>> getDefaultHooks() {
    return DEFAULT_HOOKS;
  }

  @Override
  public IGenericLoader<? extends ModifierModule> getLoader() {
    return LOADER;
  }

  public static final IGenericLoader<ConditionalMiningSpeedModule> LOADER = new IGenericLoader<>() {
    @Override
    public ConditionalMiningSpeedModule deserialize(JsonObject json) {
      boolean percent = GsonHelper.getAsBoolean(json, "percent", false);
      return new ConditionalMiningSpeedModule(
        BlockPredicate.LOADER.getAndDeserialize(json, "blocks"),
        LivingEntityPredicate.LOADER.getAndDeserialize(json, "entity"),
        GsonHelper.getAsBoolean(json, "require_effective", true),
        ModifierFormula.deserialize(json, VARIABLES, percent ? PERCENT : BOOST), percent,
        ModifierModuleCondition.deserializeFrom(json)
      );
    }

    @Override
    public void serialize(ConditionalMiningSpeedModule object, JsonObject json) {
      object.condition.serializeInto(json);
      json.add("blocks", BlockPredicate.LOADER.serialize(object.block));
      json.add("entity", LivingEntityPredicate.LOADER.serialize(object.holder));
      json.addProperty("require_effective", object.requireEffective);
      json.addProperty("percent", object.percent);
      object.formula.serialize(json, VARIABLES);
    }

    @Override
    public ConditionalMiningSpeedModule fromNetwork(FriendlyByteBuf buffer) {
      boolean percent = buffer.readBoolean();
      return new ConditionalMiningSpeedModule(
        BlockPredicate.LOADER.fromNetwork(buffer),
        LivingEntityPredicate.LOADER.fromNetwork(buffer),
        buffer.readBoolean(),
        ModifierFormula.fromNetwork(buffer, VARIABLES.length, percent ? PERCENT : BOOST), percent,
        ModifierModuleCondition.fromNetwork(buffer)
      );
    }

    @Override
    public void toNetwork(ConditionalMiningSpeedModule object, FriendlyByteBuf buffer) {
      buffer.writeBoolean(object.percent);
      BlockPredicate.LOADER.toNetwork(object.block, buffer);
      LivingEntityPredicate.LOADER.toNetwork(object.holder, buffer);
      buffer.writeBoolean(object.requireEffective);
      object.formula.toNetwork(buffer);
      object.condition.toNetwork(buffer);
    }
  };


  /* Builder */

  /** Creates a builder instance */
  public static Builder blocks(IJsonPredicate<BlockState> blocks) {
    return new Builder(blocks);
  }

  /** Builder class */
  public static class Builder extends ModifierFormula.Builder<Builder,ConditionalMiningSpeedModule> {
    private final IJsonPredicate<BlockState> blocks;
    @Setter
    @Accessors(fluent = true)
    private IJsonPredicate<LivingEntity> holder = LivingEntityPredicate.ANY;
    private boolean percent = false;
    private boolean requireEffective = true;

    private Builder(IJsonPredicate<BlockState> blocks) {
      super(VARIABLES, BOOST);
      this.blocks = blocks;
    }

    @Override
    protected FallbackFormula getFormula() {
      return percent ? PERCENT : BOOST;
    }

    /** Sets this to a percent boost formula */
    public Builder percent() {
      this.percent = true;
      return this;
    }

    /** Sets this to a percent boost formula */
    public Builder allowIneffective() {
      this.requireEffective = false;
      return this;
    }

    @Override
    protected ConditionalMiningSpeedModule build(ModifierFormula formula) {
      return new ConditionalMiningSpeedModule(blocks, holder, requireEffective, formula, percent, condition);
    }
  }
}