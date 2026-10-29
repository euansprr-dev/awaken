package dev.awaken.kin.works;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A Work Plan's state: which work, how far the Thatchbacks have got through its blocks, and what it's waiting for. */
public final class WorkPlanBlockEntity extends BlockEntity {
    public static final int SCHEMA = 1;

    /** The work's plan id ({@link WorkPlans.Plan#id}). */
    public String work = "smeltery";
    /** Blocks of the plan placed or found already right, in order. */
    public int step;
    /** What the build is waiting for, in words ("planks"), or empty. Not saved. */
    public String waiting = "";

    public WorkPlanBlockEntity(BlockPos pos, BlockState state) {
        super(WorksContent.WORK_PLAN_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) WorkPlans.loaded(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) WorkPlans.gone(this);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schema", SCHEMA);
        out.putString("work", work);
        out.putInt("step", step);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        work = in.getStringOr("work", "smeltery");
        step = in.getIntOr("step", 0);
    }
}
