package me.smickles.DynamicMarket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.nijikokun.register.payment.Methods;
import com.nijikokun.register.payment.Method.MethodAccount;

import me.smickles.DynamicMarket.Invoice;

public class DynamicMarket extends JavaPlugin {

	public static DynamicMarket plugin;
	public final Logger logger = Logger.getLogger("Minecraft");
	public Configuration items;
	public static BigDecimal MINVALUE = BigDecimal.valueOf(.01).setScale(2);
	public static BigDecimal MAXVALUE = BigDecimal.valueOf(10000).setScale(2);
	public static BigDecimal CHANGERATE = BigDecimal.valueOf(.01).setScale(2);
	
	@Override
	public void onDisable(){
		PluginDescriptionFile pdfFile = this.getDescription();
		this.logger.info(pdfFile.getName() + " disabled");
	}

	@Override
	public void onEnable() {
		PluginDescriptionFile pdfFile = this.getDescription();
		this.logger.info(pdfFile.getName() + " version " + pdfFile.getVersion() + " enabled");
		
		//item 'config'
		items = getConfiguration();
		items.load();
		String[] itemNames = new String[]{"stone","01","dirt","03","cobblestone","04","sapling","06","sand","12","gravel","13","wood","17","lapis","22","sandstone","24","grass","31","wool","35","dandelion","37","rose","38","brownmushroom","39","redmushroom","40","mossstone","48","obsidian","49","cactus","81","netherrack","87","soulsand","88","vine","106","apple","260","coal","263","diamond","264","iron","265","gold","266","string","287","feather","288","gunpowder","289","seeds","295","flint","318","pork","319","redstone","331","snow","332","leather","334","clay","337","sugarcane","338","slime","341","egg","344","glowstone","348","fish","349","bone","352","pumpkinseeds","361","melonseeds","362","beef","363","chicken","365","rottenflesh","367","enderpearl","368"};
		for(int x = 0; x < itemNames.length; x = x + 2) {
			items.getString(itemNames[x], " ");
			
		}
		for (int x = 1; x < itemNames.length; x = x + 2) {
			items.getInt(itemNames[x-1] + ".number", Integer.parseInt(itemNames[x]));
			
		}
		for (int x = 0; x < itemNames.length; x = x + 2) {
			items.getDouble(itemNames[x] + ".value", 10);
		}
		
		for (int x =0; x < itemNames.length; x = x + 2) {
			items.getDouble(itemNames[x] + ".minValue", MINVALUE.doubleValue());
		}
		
		for (int x =0; x < itemNames.length; x = x + 2) {
			items.getDouble(itemNames[x] + ".maxValue", MAXVALUE.doubleValue());
			items.getDouble(itemNames[x] + ".changeRate", CHANGERATE.doubleValue());
		}
		
		items.save();
	}
	

	
	/**
	 * Determine the cost of a given number of an item and calculate a new value for the item accordingly.
	 * @param oper 1 for buying, 0 for selling.
	 * @param item the item in question
	 * @param amount the desired amount of the item in question
	 * @return the total cost and the calculated new value as an Invoice
	 */
	public Invoice generateInvoice(int oper, String item, int amount) {
		items.load();
		// get the initial value of the item, 0 for not found
		
		Invoice inv = new Invoice(BigDecimal.valueOf(0),BigDecimal.valueOf(0));
		inv.value = BigDecimal.valueOf(items.getDouble(item + ".value", 0));
		// determine the total cost
		inv.total = BigDecimal.valueOf(0);
		for(int x = 1; x <= amount; x++) {
			BigDecimal minValue = BigDecimal.valueOf(items.getDouble(item + ".minValue", MINVALUE.doubleValue()));
			BigDecimal changeRate = BigDecimal.valueOf(items.getDouble(item + ".changeRate", CHANGERATE.doubleValue()));
			BigDecimal maxValue = BigDecimal.valueOf(items.getDouble(item + ".maxValue", MAXVALUE.doubleValue()));

			// check the current value
			if(inv.getValue().compareTo(minValue) == 1 | inv.getValue().compareTo(minValue) == 0) {
				// current value is @ or above minValue
				// be sure value is not above maxValue
				if (inv.getValue().compareTo(maxValue) == -1) {
					// current value is "just right"
					// add current value to total
					inv.total = inv.getTotal().add(inv.getValue());
				} else {
					// current value is above the max
					// add maxValue to total
					inv.total = inv.getTotal().add(maxValue);
				}
			} else {
				// current value is below the minimum
				// add the minimum to total
				inv.total = inv.getTotal().add(minValue);
			}
			
			// Change our stored value for the item
			// we don't care about min/maxValue here because we don't want the value to 'bounce' off of them.
			if (oper == 1) {
				inv.value = inv.getValue().add(changeRate);
			} else if (oper == 0) {
				inv.value = inv.getValue().subtract(changeRate);
			} else {
				return null;
			}
		}
		return inv;
	}
	

	/**
	 * Buy a specified amount of an item for the player.
	 * 
	 * @param player The player on behalf of which these actions will be carried out. 
	 * @param item The desired item in the form of the item name. 
	 * @param amount The desired amount of the item to purchase.
	 * @return true on success, false on failure. 
	 */
	public boolean buy (Player player, String item, int amount) {
		
		// Be sure we have a positive amount
		if (amount < 0) {
			player.sendMessage(ChatColor.RED + "Invalid amount.");
			player.sendMessage("No negative numbers, please.");
			return false;
		}
		items.load();
		int id = items.getInt(item + ".number", 0);
		// a value of 0 would indicate that we did not find an item with that name
		if(id != 0) {
			// determine what it will cost 
			Invoice invoice = generateInvoice(1, item, amount);
			MethodAccount cash = Methods.getMethod().getAccount(player.getName());
			if(cash.hasEnough(invoice.getTotal().doubleValue())) {
				ItemStack its = new ItemStack(id,amount);
				player.getInventory().addItem(its);
				items.setProperty(item + ".value", invoice.getValue());
				items.save();
				// Give some nice output.
				player.sendMessage(ChatColor.GREEN + "Old Balance: " + ChatColor.WHITE + BigDecimal.valueOf(cash.balance()).setScale(2, RoundingMode.HALF_UP));
				// Subtract the invoice (this is an efficient place to do this)
				cash.subtract(invoice.getTotal().doubleValue());
				player.sendMessage(ChatColor.GREEN + "Cost: " + ChatColor.WHITE + invoice.getTotal());
				player.sendMessage(ChatColor.GREEN + "New Balance: " + ChatColor.WHITE + BigDecimal.valueOf(cash.balance()).setScale(2, RoundingMode.HALF_UP));
				return true;
			}else{
				// Otherwise, give nice output anyway ;)
				// The idea here is to show how much more money is needed.
				BigDecimal difference = BigDecimal.valueOf(cash.balance() - invoice.getTotal().doubleValue()).setScale(2, RoundingMode.HALF_UP);
				player.sendMessage(ChatColor.RED + "You don't have enough money");
				player.sendMessage(ChatColor.GREEN + "Balance: " + ChatColor.WHITE + BigDecimal.valueOf(cash.balance()).setScale(2, RoundingMode.HALF_UP));
				player.sendMessage(ChatColor.GREEN + "Cost: " + ChatColor.WHITE + invoice.getTotal());
				player.sendMessage(ChatColor.GREEN + "Difference: " + ChatColor.RED + difference);
				return false;
			}
		}else{
			player.sendMessage(ChatColor.RED + "Not allowed to buy that item.");
			player.sendMessage("Be sure you typed the correct name");
			return false;
		}
	}
	
	/**
	 * Figure out how much of a given item is in the player's inventory
	 * @param player The player entity in question.
	 * @param id The Data Value of the item in question.
	 * @return The amount of the item in the player's inventory as an integer.
	 */
	public int getAmountInInventory(Player player, int id) {
		int inInventory = 0;
		for (ItemStack slot : player.getInventory().all(id).values()) {
			inInventory += slot.getAmount();
		}
		return inInventory;
	}
	
	/**
	 * Sell a specified amount of an item for the player.
	 * 
	 * @param player The player on behalf of which these actions will be carried out. 
	 * @param item The desired item in the form of the item name. 
	 * @param amount The desired amount of the item to sell.
	 * @return true on success, false on failure. 
	 */
	public boolean sell (Player player, String item, int amount) {
		
		// Be sure we have a positive amount
		if (amount < 0) {
			player.sendMessage(ChatColor.RED + "Invalid amount.");
			player.sendMessage("No negative numbers, please.");
			return false;
		}
		items.load();
		int id = items.getInt(item + ".number", 0);
		// a value of 0 would indicate that we did not find an item with that name
		if(id != 0) {
			// determine what it will pay 
			Invoice invoice = generateInvoice(0, item, amount);
			MethodAccount cash = Methods.getMethod().getAccount(player.getName());
			// If the player has enough of the item, perform the transaction.	
			if (player.getInventory().contains(id, amount)) {
				// Figure out how much is left over.
				int left = getAmountInInventory(player,id) - amount;
				// Take out all of the item
				player.getInventory().remove(id);
				// put back what was left over
				if(left > 0) {
					ItemStack its = new ItemStack(id,left);
					player.getInventory().addItem(its);
				}
				items.setProperty(item + ".value", invoice.getValue());
				// record the change in value
				items.save();
				// give some nice output
				player.sendMessage(ChatColor.GREEN + "Old Balance: " + ChatColor.WHITE + BigDecimal.valueOf(cash.balance()).setScale(2, RoundingMode.HALF_UP));
				cash.add(invoice.getTotal().doubleValue());
				player.sendMessage(ChatColor.GREEN + "Sale: " + ChatColor.WHITE + invoice.total);
				player.sendMessage(ChatColor.GREEN + "New Balance: " + ChatColor.WHITE + BigDecimal.valueOf(cash.balance()).setScale(2, RoundingMode.HALF_UP));
				return true;
			}else{
				// give nice output even if they gave a bad number.
				player.sendMessage(ChatColor.RED + "You don't have enough " + item);
				player.sendMessage(ChatColor.GREEN + "In Inventory: " + ChatColor.WHITE + getAmountInInventory(player, id));
				player.sendMessage(ChatColor.GREEN + "Attempted Amount: " + ChatColor.WHITE + amount);
				return false;
			}
		}else{
			player.sendMessage(ChatColor.RED + "Not allowed to buy that item.");
			player.sendMessage("Be sure you typed the correct name");
			return false;
		}
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		return readCommand((Player) sender, commandLabel, args);
	}
	
	public boolean readCommand(Player player, String command, String[] args) {
		if(command.equalsIgnoreCase("buy")) {
			if(args.length == 2) {
				String item = args[0];
				int amount = 0;
				try {
					amount = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					player.sendMessage(ChatColor.RED + "Invalid amount.");
					player.sendMessage("Be sure you typed a whole number.");
					return false;
				}
				return buy(player, item, amount);
			} else {
				player.sendMessage("Invalid number of arguments");
				return false;
			}

		} else if (command.equalsIgnoreCase("sell")) {
			if (args.length == 1) {
				if (args[0].equalsIgnoreCase("all")) {
					return sellAll(player);
				}
			} else if (args.length == 2) {
				String item = args[0];
				int amount = 0;
				try {
					amount = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					player.sendMessage(ChatColor.RED + "Invalid amount.");
					player.sendMessage("Be sure you typed a whole number.");
					return false;
				}
				return sell(player, item, amount);
			} else {
				player.sendMessage("Invalid number of arguments");
				return false;
			}
		// Command Example: /price cobblestone
		// should return: cobblestone: .01
		}else if(command.equalsIgnoreCase("price")){
			// We expect one argument
			if(args.length == 1){
				// Load the item list
				items.load();
				// get the price of the given item, if it's an invalid item set our variable to -2000000000 (an unlikely number to receive 'naturally')
				BigDecimal price = BigDecimal.valueOf(items.getDouble(args[0] + ".value", -2000000000));
				BigDecimal minValue = BigDecimal.valueOf(items.getDouble(args[0] + ".minValue", MINVALUE.doubleValue()));
				if(price.intValue() != -2000000000) {
					// We received an argument which resolved to an item on our list.
					// The price could register as a negative or below .01
					// in this case we should return .01 as the price.
					if(price.compareTo(minValue) == -1) {
						price = minValue;
					}
					player.sendMessage(ChatColor.GREEN + args[0] + ": " + ChatColor.WHITE + price);
					return true;
				}else{
					// We received an argument which did not resolve to a known item.
					player.sendMessage(ChatColor.RED + "Be sure you typed the correct name");
					player.sendMessage(args[0] + ChatColor.RED + " is invalid");
					return false;
				}
			}else{
				// We received too many or too few arguments.
				player.sendMessage("Invalid Arguments");
				return false;
			}
		// Example: '/market top' should return the top 5 most expensive items on the market
		// '/market bottom' should do the dame for the least expensive items.
		}else if(command.equalsIgnoreCase("market")) {
			// we expect one argument
			if(args.length == 1) {
				// We received '/market top'
				if(args[0].equalsIgnoreCase("top")) {
					// load the item list
					items.load();
					// make  'arrays', a name, a price 
					List<String> names = items.getKeys();
					String board[][] = new String[names.size()][2];
					for(int x = 0; x < names.size(); x++) {
						// names
						board[x][1] = names.get(x);
						// prices
						board[x][0] = String.valueOf(items.getDouble(names.get(x) + ".value", -200000000));
					}
					//sort 'em
					Arrays.sort(board, new Comparator<String[]>() {

						@Override
						public int compare(String[] entry1, String[] entry2) {
							final BigDecimal value1 = BigDecimal.valueOf(Double.valueOf(entry1[0]));
							final BigDecimal value2 = BigDecimal.valueOf(Double.valueOf(entry2[0]));
							return value2.compareTo(value1);
						}

						
					});
					// Send them to the player
					for(int x = 0; x < 10; x++) {
						player.sendMessage(board[x][0] + " " + board[x][1]);
					}
					return true;
				}else if(args[0].equalsIgnoreCase("bottom")) {
					// load the item list
					items.load();
					// make  'arrays', a name, a price 
					List<String> names = items.getKeys();
					String board[][] = new String[names.size()][2];
					for(int x = 0; x < names.size(); x++) {
						// names
						board[x][1] = names.get(x);
						// prices
						board[x][0] = String.valueOf(items.getDouble(names.get(x) + ".value", -200000000));
					}
					//sort 'em
					Arrays.sort(board, new Comparator<String[]>() {

						@Override
						public int compare(String[] entry1, String[] entry2) {
							final BigDecimal value1 = BigDecimal.valueOf(Double.valueOf(entry1[0]));
							final BigDecimal value2 = BigDecimal.valueOf(Double.valueOf(entry2[0]));
							return value1.compareTo(value2);
						}

						
					});
					// Send them to the player
					for(int x = 0; x < 10; x++) {
						player.sendMessage(board[x][0] + " " + board[x][1]);
					}
					return true;					
				}
			}
			player.sendMessage("Invalid number of arguments");
		}
		return false;
	}
	
	private boolean sellAll(Player player) {
		items.load();
		List<String> names = items.getKeys();
		int[] id = new int[names.size()];
		BigDecimal[] value = new BigDecimal[names.size()];
		BigDecimal sale = BigDecimal.ZERO.setScale(2);
		
		// make a 'list' of all sellable items with their id's and values
		for (int x = 0; x < names.size(); x++) {
			id[x] = items.getInt(names.get(x) + ".number", 0);
			value[x] = BigDecimal.valueOf(items.getDouble(names.get(x) + ".value", 0)).setScale(2, RoundingMode.HALF_UP);	
		}
		
		// run thru each slot and sell any sellable items
		for (int index = 0; index < 35; index++) {
			ItemStack slot = player.getInventory().getItem(index);
			int slotId = slot.getTypeId();
			BigDecimal slotAmount = new BigDecimal(slot.getAmount()).setScale(0, RoundingMode.HALF_UP);
			
			for (int x = 0; x < names.size(); x++) {
				if (id[x] == slotId) {
					// perform sale of this slot
					Invoice thisSale = generateInvoice(0, names.get(x), slotAmount.intValue());
					// rack up our total
					sale = sale.add(thisSale.getTotal());
					// save the new value
					items.setProperty(names.get(x) + ".value", thisSale.getValue());
					items.save();
					// remove the item(s)
					player.getInventory().removeItem(slot);
					// "pay the man"
					MethodAccount cash = Methods.getMethod().getAccount(player.getName());
					cash.add(thisSale.getTotal().doubleValue());
					// give nice output
					player.sendMessage(ChatColor.GREEN + "Sold " + ChatColor.WHITE + slotAmount + " " + ChatColor.GRAY + names.get(x) + ChatColor.GREEN + " for " + ChatColor.WHITE + thisSale.getTotal());
				}
			}
			
		}
		
		// give a nice total collumn
		if (sale == BigDecimal.ZERO.setScale(2))
			player.sendMessage("Nothing to Sell");
		player.sendMessage(ChatColor.GREEN + "--------------------------------");
		player.sendMessage(ChatColor.GREEN + "Total Sale: " + ChatColor.WHITE + sale);
		return true;
	}

	public static double round2(double num) {
		double result = num * 100;
		result = Math.round(result);
		result = result / 100;
		return result;		
	}
}
