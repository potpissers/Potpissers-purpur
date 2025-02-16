package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class LecternMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 1;
    private static final int SLOT_COUNT = 1;
    public static final int BUTTON_PREV_PAGE = 1;
    public static final int BUTTON_NEXT_PAGE = 2;
    public static final int BUTTON_TAKE_BOOK = 3;
    public static final int BUTTON_PAGE_JUMP_RANGE_START = 100;
    private final Container lectern;
    private final ContainerData lecternData;

    public LecternMenu(int containerId) {
        this(containerId, new SimpleContainer(1), new SimpleContainerData(1));
    }

    public LecternMenu(int containerId, Container lectern, ContainerData lecternData) {
        super(MenuType.LECTERN, containerId);
        checkContainerSize(lectern, 1);
        checkContainerDataCount(lecternData, 1);
        this.lectern = lectern;
        this.lecternData = lecternData;
        this.addSlot(new Slot(lectern, 0, 0, 0) {
            @Override
            public void setChanged() {
                super.setChanged();
                LecternMenu.this.slotsChanged(this.container);
            }
        });
        this.addDataSlots(lecternData);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 100) {
            int i = id - 100;
            this.setData(0, i);
            return true;
        } else {
            switch (id) {
                case 1: {
                    int i = this.lecternData.get(0);
                    this.setData(0, i - 1);
                    return true;
                }
                case 2: {
                    int i = this.lecternData.get(0);
                    this.setData(0, i + 1);
                    return true;
                }
                case 3:
                    if (!player.mayBuild()) {
                        return false;
                    }

                    ItemStack itemStack = this.lectern.removeItemNoUpdate(0);
                    this.lectern.setChanged();
                    if (!player.getInventory().add(itemStack)) {
                        player.drop(itemStack, false);
                    }

                    return true;
                default:
                    return false;
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.lectern.stillValid(player);
    }

    public ItemStack getBook() {
        return this.lectern.getItem(0);
    }

    public int getPage() {
        return this.lecternData.get(0);
    }
}
