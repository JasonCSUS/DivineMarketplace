/**
 * Plain-Java concurrency helpers for marketplace runtime safety.
 *
 * <p>Classes in this package must not touch Bukkit/Paper objects directly. They
 * exist so GUI commands/listeners can keep Minecraft API work on the main thread
 * while protecting async-ready marketplace operations from duplicate input and
 * shared-object races.</p>
 */
package divinejason.divinemarketplace.concurrency;
