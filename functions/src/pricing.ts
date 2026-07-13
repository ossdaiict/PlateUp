import * as admin from "firebase-admin";

export interface CartItem {
    menuItemId: string;
    quantity: number;
}

export interface PricingResult {
    itemsTotal: number;
    packagingFee: number;
    totalAmount: number;
    validatedItems: any[];
}

export async function calculateOrderTotal(
    canteenId: string,
    items: CartItem[],
    orderType: string = "DINE_IN"
): Promise<PricingResult> {
    const canteenSnapshot = await admin.database().ref(`canteens/${canteenId}`).once("value");
    const canteen = canteenSnapshot.val();
    if (!canteen) throw new Error("Canteen not found");

    const packagingFeePerItem = Number(canteen.packagingFee || 0);

    const menuSnapshot = await admin.database().ref(`menus/${canteenId}`).once("value");
    const menu = menuSnapshot.val();
    if (!menu) throw new Error("Menu not found");

    let itemsTotal = 0;
    const validatedItems = [];

    for (const item of items) {
        if (!item || typeof item.menuItemId !== "string" || item.menuItemId.trim().length === 0) {
            throw new Error("Cart item is missing its menu item ID. Refresh the menu and add the item again.");
        }
        if (!Number.isInteger(item.quantity) || item.quantity <= 0) {
            throw new Error(`Invalid quantity for menu item ${item.menuItemId}`);
        }
        const menuItem = menu[item.menuItemId];
        if (!menuItem) throw new Error(`Item ${item.menuItemId} not found in menu`);
        if (!menuItem.available) throw new Error(`Item ${menuItem.name} is currently unavailable`);

        itemsTotal += (menuItem.price || 0) * item.quantity;
        validatedItems.push({
            ...menuItem,
            // The RTDB key is the authoritative menu identifier. Persist it
            // explicitly because older menu records may not contain `id`.
            menuItemId: item.menuItemId,
            quantity: item.quantity
        });
    }

    const packagingFee = orderType === "TAKEAWAY"
        ? packagingFeePerItem * items.reduce((total, item) => total + item.quantity, 0)
        : 0;

    return {
        itemsTotal,
        packagingFee,
        totalAmount: itemsTotal + packagingFee,
        validatedItems
    };
}
