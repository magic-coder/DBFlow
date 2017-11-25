package com.raizlabs.android.dbflow.structure.database.transaction

import android.os.Looper

import com.raizlabs.android.dbflow.config.FlowLog
import java.util.concurrent.LinkedBlockingQueue

/**
 * Description: Handles concurrent requests to the database and puts them in FIFO order based on a
 * [LinkedBlockingQueue]. As requests come in, they're placed in order and ran one at a time
 * until the queue becomes empty.
 */
class DefaultTransactionQueue
/**
 * Creates a queue with the specified name to ID it.
 *
 * @param name
 */
(name: String) : Thread(name), ITransactionQueue {

    private val queue = LinkedBlockingQueue<Transaction>()

    private var isQuitting = false

    override fun run() {
        Looper.prepare()
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        var transaction: Transaction
        while (true) {
            try {
                transaction = queue.take()
            } catch (e: InterruptedException) {
                synchronized(this) {
                    if (isQuitting) {
                        synchronized(queue) {
                            queue.clear()
                        }
                        return
                    }
                }
                continue
            }

            if (!isQuitting) {
                transaction.executeSync()
            }
        }
    }

    override fun add(runnable: Transaction) {
        synchronized(queue) {
            if (!queue.contains(runnable)) {
                queue.add(runnable)
            }
        }
    }

    /**
     * Cancels the specified request.
     *
     * @param transaction
     */
    override fun cancel(transaction: Transaction) {
        synchronized(queue) {
            if (queue.contains(transaction)) {
                queue.remove(transaction)
            }
        }
    }

    /**
     * Cancels all requests by a specific tag
     *
     * @param name
     */
    override fun cancel(name: String) {
        synchronized(queue) {
            val it = queue.iterator()
            while (it.hasNext()) {
                val next = it.next()
                if (next.name() != null && next.name() == name) {
                    it.remove()
                }
            }
        }
    }

    override fun startIfNotAlive() {
        synchronized(this) {
            if (!isAlive) {
                try {
                    start()
                } catch (i: IllegalThreadStateException) {
                    // log if failure from thread is still alive.
                    FlowLog.log(FlowLog.Level.E, throwable = i)
                }

            }
        }
    }

    /**
     * Quits this process
     */
    override fun quit() {
        synchronized(this) {
            isQuitting = true
        }
        interrupt()
    }
}

