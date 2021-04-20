package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.DeliveryOrderContract
import com.template.states.DeliveryOrderState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


/*
    Parties:
    -Retail
    - Distributor
 */

@InitiatingFlow
@StartableByRPC
class ReceiveDeliveryOrderFlowInitiator(
    private val deliveryOrderStateID: UniqueIdentifier
) : FlowLogic<Unit>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() {
        val deliveryOrderStateAndRef = serviceHub
            .vaultService
            .queryBy<DeliveryOrderState>(
                QueryCriteria.LinearStateQueryCriteria(linearId = listOf(deliveryOrderStateID))
            )
            .states
            .single()

        receiveDeliveryOrder(deliveryOrderStateAndRef)
        addProductsToStock(deliveryOrderStateAndRef)
    }

    @Suspendable
    private fun receiveDeliveryOrder(deliveryOrderStateAndRef: StateAndRef<DeliveryOrderState>){
        val transactionBuilder = TransactionBuilder(notary = deliveryOrderStateAndRef.state.notary)

        transactionBuilder
            .addInputState(deliveryOrderStateAndRef)
            .addCommand(
                data = DeliveryOrderContract.Commands.Receive(),
                keys = listOf(ourIdentity.owningKey)
            )

        transactionBuilder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)
        subFlow(FinalityFlow(signedTransaction, listOf()))
    }

    @Suspendable
    private fun addProductsToStock(deliveryOrderStateAndRef: StateAndRef<DeliveryOrderState>){
        deliveryOrderStateAndRef.state.data.products.forEach { (_, products) ->
            products.forEach { product ->
                subFlow(AddProductFlowInitiator(product))
            }
        }
    }


}
