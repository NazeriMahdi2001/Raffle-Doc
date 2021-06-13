package org.ergoplatform.appkit

import java.math.BigInteger

import io.circe.{Json, parser}
import scalaj.http.{Http, HttpResponse}
import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.config.{ErgoNodeConfig, ErgoToolConfig}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import special.sigma.GroupElement
import org.ergoplatform.appkit.{ErgoWallet, Parameters}
import scorex.crypto.hash.Digest32
import special.collection.Coll

import scala.collection.JavaConverters._

object ContractTest {

  val conf: ErgoToolConfig = ErgoToolConfig.load("test.conf")
  val nodeConf: ErgoNodeConfig = conf.getNode
  val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf)

  val addrEnc = new ErgoAddressEncoder(NetworkType.MAINNET.networkPrefix)
  val secureRandom = new java.security.SecureRandom

  def randBigInt: BigInt = new BigInteger(256, secureRandom)

  def getProveDlogAddress(z: BigInt, ctx: BlockchainContext): String = {
    val g: GroupElement = CryptoConstants.dlogGroup.generator
    val gZ: GroupElement = g.exp(z.bigInteger)
    val contract = ctx.compileContract(
      ConstantsBuilder.create()
        .item(
          "gZ", gZ
        ).build(), "{proveDlog(gZ)}"
    )
    addrEnc.fromProposition(contract.getErgoTree).get.toString
  }

  def randomAddr(): Unit = {
    ergoClient.execute((ctx: BlockchainContext) => {
      val rnd = randBigInt
      println(s"secret: ${rnd.toString(16)}")
      val addr = getProveDlogAddress(rnd, ctx)
      println(s"pk/address: ${addr}")
    })
  }

  def makeRaffle(ctx: BlockchainContext): Unit = {
    val TicketScript =
      s"""{
         |  val refundPhaseSpend = HEIGHT > deadlineHeight &&
         |                         blake2b256(INPUTS(0).propositionBytes) == SELF.R6[Coll[Byte]].get &&
         |                         INPUTS(0).tokens(0)._1 == SELF.tokens(0)._1
         |
         |  val winnerPhaseSpend = HEIGHT > deadlineHeight &&
         |                         blake2b256(INPUTS(0).propositionBytes) == winnerScriptHash &&
         |                         INPUTS(0).tokens(0)._1 == SELF.tokens(0)._1
         |
         |  val receiverCheck = OUTPUTS(1).propositionBytes  == SELF.R7[Coll[Byte]].get &&
         |                      OUTPUTS(1).value == SELF.tokens(0)._2 * ticketPrice &&
         |                      INPUTS.size == 2
         |
         |  val receiverCheckWinner = OUTPUTS(0).propositionBytes == SELF.R7[Coll[Byte]].get &&
         |                            OUTPUTS(0).value == INPUTS(0).value
         |
         |  sigmaProp((receiverCheck && refundPhaseSpend) || (receiverCheckWinner && winnerPhaseSpend))
         |}""".stripMargin

    val winnerScript =
      s"""{
         |  sigmaProp(
         |    allOf(Coll(
         |          // Valid Ticket
         |          INPUTS(1).tokens(0)._1 == SELF.tokens(0)._1,
         |          INPUTS(1).R4[Long].get <= SELF.R4[Long].get,
         |          INPUTS(1).R4[Long].get + INPUTS(1).R5[Long].get > SELF.R4[Long].get
         |    ))
         |  )
         |}""".stripMargin

    val scriptTokenRepo =
      s"""{
         |  val totalSoldTicket = SELF.R4[Long].get
         |  val totalSoldTicketBI: BigInt = totalSoldTicket.toBigInt
         |  val totalRaised = totalSoldTicket * ticketPrice
         |  val charityCoef = SELF.R5[Long].get
         |  val projectCoef = SELF.R6[Long].get
         |  val winnerCoef = 100L - charityCoef - projectCoef
         |  sigmaProp(
         |    if (HEIGHT < deadlineHeight) {
         |      allOf(Coll(
         |            // validate Script
         |            OUTPUTS(0).propositionBytes == SELF.propositionBytes,
         |            blake2b256(OUTPUTS(1).propositionBytes) == ticketScriptHash,
         |            OUTPUTS(1).R6[Coll[Byte]].get == blake2b256(SELF.propositionBytes),
         |            // minERG
         |            INPUTS(1).value >= ticketPrice + 2 * minFee,
         |            // validate Register
         |            OUTPUTS(0).R4[Long].get == totalSoldTicket + (INPUTS(1).value - 2 * minFee) / ticketPrice,
         |            OUTPUTS(1).R4[Long].get == totalSoldTicket,
         |            OUTPUTS(1).R5[Long].get == (INPUTS(1).value - 2 * minFee) / ticketPrice,
         |            // validate Token
         |            OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1,
         |            OUTPUTS(0).tokens(0)._2 == SELF.tokens(0)._2 - (INPUTS(1).value - 2 * minFee) / ticketPrice,
         |            OUTPUTS(0).tokens(1)._1 == SELF.tokens(1)._1, // Raffle Service Token
         |            OUTPUTS(1).tokens(0)._1 == SELF.tokens(0)._1,
         |            OUTPUTS(1).tokens(0)._2 == (INPUTS(1).value - 2 * minFee) / ticketPrice,
         |            // ERG Protect
         |            OUTPUTS(0).value == SELF.value + INPUTS(1).value - 2 * minFee,
         |            OUTPUTS(1).value == minFee,
         |            // same Coef
         |            OUTPUTS(0).R5[Long].get == charityCoef,
         |            OUTPUTS(0).R6[Long].get == projectCoef
         |            ))
         |    }
         |    else {
         |      if (totalRaised >= minToRaise) {
         |        allOf(Coll(
         |              // Validate Size
         |              INPUTS.size == 1 && OUTPUTS.size == 5,
         |              // Pay Back Raffle Service Token
         |              OUTPUTS(0).tokens(0)._1 == SELF.tokens(1)._1,
         |              OUTPUTS(0).tokens(0)._2 == 1,
         |              OUTPUTS(0).propositionBytes == servicePubKey.propBytes,
         |              // Charity Box
         |              OUTPUTS(1).value >= totalRaised * charityCoef / 100,
         |              OUTPUTS(1).propositionBytes == charityPubKey.propBytes,
         |              // Project Box
         |              OUTPUTS(2).value >= totalRaised * projectCoef / 100,
         |              OUTPUTS(2).propositionBytes == servicePubKey.propBytes,
         |              // Validate Seed
         |              CONTEXT.dataInputs(0).tokens(0)._1 == oracleNebulaNFT,
         |              // Winner Box
         |              OUTPUTS(3).value  >= totalRaised * winnerCoef / 100,
         |              blake2b256(OUTPUTS(3).propositionBytes) == winnerScriptHash,
         |              OUTPUTS(3).R4[Long].get == ((byteArrayToBigInt(CONTEXT.dataInputs(0).id.slice(0, 15)).toBigInt + totalSoldTicketBI) % totalSoldTicketBI).toBigInt,
         |              OUTPUTS(3).tokens(0)._1 == SELF.tokens(0)._1,
         |              OUTPUTS(3).tokens(0)._2 == SELF.tokens(0)._2
         |         ))
         |      }
         |      else {
         |      if (totalRaised < minToRaise) {
         |        if(totalSoldTicket > 0){
         |          allOf(Coll(
         |                // validate Script
         |                OUTPUTS(0).propositionBytes == SELF.propositionBytes,
         |                // validate Token & ERG
         |                OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1,
         |                OUTPUTS(0).value >= SELF.value - (OUTPUTS(0).tokens(0)._2 - SELF.tokens(0)._2) * ticketPrice,
         |                OUTPUTS(0).tokens(0)._2 > SELF.tokens(0)._2,
         |                OUTPUTS(0).R4[Long].get == SELF.R4[Long].get - (OUTPUTS(0).tokens(0)._2 - SELF.tokens(0)._2)
         |          ))
         |        }
         |        else
         |        {
         |          allOf(Coll(
         |                // Pay Back Raffle Service Token
         |                OUTPUTS(0).tokens(0)._1 == SELF.tokens(1)._1,
         |                OUTPUTS(0).tokens(0)._2 == 1,
         |                OUTPUTS(0).propositionBytes == servicePubKey.propBytes
         |          ))
         |        }
         |      }
         |      else {
         |        false
         |      }
         |    }
         |  })
         |}""".stripMargin

    val scriptRaffleService = // may not be used
      s"""{
         |  servicePubKey
         |}""".stripMargin

    val servicePubKey = "9faLY6U5W6RrCHdUJ4wSqctKYik9skpQDVDEETPqaX9uXWkjEva"
    val serviceSecret = BigInt("51377199ca726394197759ebf50bf2d3dc73e464a422720921f734932164bf3e", 16)
    val serviceAddress : Address = Address.create(servicePubKey)
    val raffleProjectPubKey = "9guri1oTv5ZFH6uMgVgDp18jVVsJmTFoS9XvJyRm6n7vKSXouML"
    val raffleProjectSecret = BigInt("4304f047d0e1c447001cb63e40ce59f8ccdd1dfabe925e39b54299a20c83240c", 16)
    val raffleProjectAddress : Address = Address.create(raffleProjectPubKey)
    val participantPubKey = "9ed9EeiuD8End4fHzTDSaGmqkRYWtS36UGqwNauxBGpmdAaUtg9"
    val participantSecret = BigInt("ca7b4f0905c238cf752a22cbcb86ccf223c7fc2f0eb244641587b60f25c4a0ac", 16)
    val participantAddress : Address = Address.create(participantPubKey)
    val charityPubKey = "9hMYhAgWCyGmpKQLcLSzzXZ788SeQprntE5agz1T5XGKKrsxoGq"
    val charitySecret = BigInt("370e394bbece18def71582b12283522aea3ca940af27c552d3508ef19c161f85", 16)
    val charityAddress : Address = Address.create(charityPubKey)

    val raffleTokenId: String = "298cbf467b7c5fd38fd3dd8cea35d6c3911f6960db6f6a66548f242a41742870"
    val serviceTokenId: String = "398cbf467b7c5fd38fd3dd8cea35d6c3911f6960db6f6a66548f242a41742870"
    val oracleId = "011d3364de07e5a26f0c4eef0852cddb387039a921b7154ef3cab22c6eda887f"
    val response: HttpResponse[String] = Http("https://api.ergoplatform.com/api/v1/boxes/unspent/byTokenId/").header("Accept", "application/json").asString



    val oracleAddress =  "EfS5abyDe4vKFrJ48K5HnwTqa1ksn238bWFPe84bzVvCGvK1h2B7sgWLETtQuWwzVdBaoRZ1HcyzddrxLcsoM5YEy4UnqcLqMU1MDca1kLw9xbazAM6Awo9y6UVWTkQcS97mYkhkmx2Tewg3JntMgzfLWz5mACiEJEv7potayvk6awmLWS36sJMfXWgnEfNiqTyXNiPzt466cgot3GLcEsYXxKzLXyJ9EfvXpjzC2abTMzVSf1e17BHre4zZvDoAeTqr4igV3ubv2PtJjntvF2ibrDLmwwAyANEhw1yt8C8fCidkf3MAoPE6T53hX3Eb2mp3Xofmtrn4qVgmhNonnV8ekWZWvBTxYiNP8Vu5nc6RMDBv7P1c5rRc3tnDMRh2dUcDD7USyoB9YcvioMfAZGMNfLjWqgYu9Ygw2FokGBPThyWrKQ5nkLJvief1eQJg4wZXKdXWAR7VxwNftdZjPCHcmwn6ByRHZo9kb4Emv3rjfZE"


    {
      // Test Case 1 : Participant Ticket Buying from Valid Raffle
      // INPUT1 : Raffle Box - INPUT2 : Participant Input
      // OUTPUT1: Raffle Box - OUTPUT2: Ticket
      val winnerContract = ctx.compileContract(
        ConstantsBuilder.create()
          .build(),
        winnerScript)

      val winnerErgoTree = winnerContract.getErgoTree
      val winnerScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(winnerErgoTree.bytes)

      val ticketContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("deadlineHeight", 50000000)
          .item("winnerScriptHash", winnerScriptHash)
          .item("ticketPrice", 1000000L)
          .item("projectPubKey", raffleProjectAddress.getPublicKey)
          .build(),
        TicketScript)

      val ticketErgoTree = ticketContract.getErgoTree
      val ticketScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(ticketErgoTree.bytes)

      val raffleContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("tokenId", ErgoId.create(raffleTokenId).getBytes)
          .item("ticketPrice", 1000000L)
          .item("minToRaise", 600000000000L)
          .item("deadlineHeight", 50000000)
          .item("charityPubKey", charityAddress.getPublicKey)
          .item("servicePubKey", serviceAddress.getPublicKey)
          .item("winnerScriptHash", winnerScriptHash)
          .item("minFee", 1000000L)
          .item("ticketScriptHash", ticketScriptHash)
          .item("oracleNebulaNFT", ErgoId.create(oracleId).getBytes)
          .build(),
        scriptTokenRepo)

      val raffleErgoTree = raffleContract.getErgoTree
      val scriptTokenRepoHash: Digest32 = scorex.crypto.hash.Blake2b256(raffleErgoTree.bytes)

      val txB = ctx.newTxBuilder()

      // R4: Soled Tickets, R5: CharityCoef, R6: ProjectCoef
      val in1 = txB.outBoxBuilder()
        .value(1000000L)
        .contract(raffleContract)
        .tokens(new ErgoToken(raffleTokenId, 1000), new ErgoToken(serviceTokenId, 1))
        .registers(ErgoValue.of(0L), ErgoValue.of(50L), ErgoValue.of(10L))
        .build()
        .convertToInputWith("0ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", 0)

      val in2 = txB.outBoxBuilder()
        .value(20000000L + 2000000L)
        .contract(new ErgoTreeContract(participantAddress._address.script))
        .build()
        .convertToInputWith("1ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", 0)

      val out1 = txB.outBoxBuilder()
        .value(1000000L + 20000000L)
        .contract(raffleContract)
        .tokens(new ErgoToken(raffleTokenId, 980), new ErgoToken(serviceTokenId, 1))
        .registers(ErgoValue.of(20L), ErgoValue.of(50L), ErgoValue.of(10L))
        .build()

      val propByte = new ErgoTreeContract(participantAddress._address.script)
      val out2 = txB.outBoxBuilder()
        .value(1000000L)
        .contract(ticketContract)
        .registers(ErgoValue.of(0L), ErgoValue.of(20L), ErgoValue.of(scriptTokenRepoHash),
          ErgoValue.of(propByte.getErgoTree.bytes))
        .tokens(new ErgoToken(raffleTokenId, 20))
        .build()

      val tx = txB.boxesToSpend(Seq(in1, in2).asJava)
        .fee(1000000)
        .outputs(out1, out2)
        .sendChangeTo(participantAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder()
        .withDLogSecret(participantSecret.bigInteger)
        .build()
      val signedTx = prover.sign(tx)

      println(signedTx.toJson(false))
    }

    {
      // Test Case 2 : Unsuccessful Raffle Ticket Refunding By Participant
      // INPUT1 : Raffle Box - INPUT2 : Ticket
      // OUTPUT1: Raffle Box - OUTPUT2: Refunded Box
      val winnerContract = ctx.compileContract(
        ConstantsBuilder.create()
          .build(),
        winnerScript)

      val winnerErgoTree = winnerContract.getErgoTree
      val winnerScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(winnerErgoTree.bytes)

      val ticketContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("deadlineHeight", 50)
          .item("winnerScriptHash", winnerScriptHash)
          .item("ticketPrice", 1000000L)
          .item("projectPubKey", raffleProjectAddress.getPublicKey)
          .build(),
        TicketScript)

      val ticketErgoTree = ticketContract.getErgoTree
      val ticketScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(ticketErgoTree.bytes)

      val raffleContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("tokenId", ErgoId.create(raffleTokenId).getBytes)
          .item("ticketPrice", 1000000L)
          .item("minToRaise", 600000000000L)
          .item("deadlineHeight", 50)
          .item("charityPubKey", charityAddress.getPublicKey)
          .item("servicePubKey", serviceAddress.getPublicKey)
          .item("winnerScriptHash", winnerScriptHash)
          .item("minFee", 1000000L)
          .item("ticketScriptHash", ticketScriptHash)
          .item("oracleNebulaNFT", ErgoId.create(oracleId).getBytes)
          .build(),
        scriptTokenRepo)

      val raffleErgoTree = raffleContract.getErgoTree
      val scriptTokenRepoHash: Digest32 = scorex.crypto.hash.Blake2b256(raffleErgoTree.bytes)

      val txB = ctx.newTxBuilder()

      // R4: Soled Tickets, R5: CharityCoef, R6: ProjectCoef
      val in1 = txB.outBoxBuilder()
        .value(1000000L + 20000000L)
        .contract(raffleContract)
        .tokens(new ErgoToken(raffleTokenId, 980), new ErgoToken(serviceTokenId, 1))
        .registers(ErgoValue.of(20L), ErgoValue.of(50L), ErgoValue.of(10L))
        .build()
        .convertToInputWith("0ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", 0)

      val propByte = new ErgoTreeContract(participantAddress._address.script)
      val in2 = txB.outBoxBuilder()
        .value(1000000L)
        .contract(ticketContract)
        .registers(ErgoValue.of(0L), ErgoValue.of(20L), ErgoValue.of(scriptTokenRepoHash),
          ErgoValue.of(propByte.getErgoTree.bytes))
        .tokens(new ErgoToken(raffleTokenId, 20))
        .build()
        .convertToInputWith("1ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", 0)

      val out1 = txB.outBoxBuilder()
        .value(1000000L)
        .contract(raffleContract)
        .tokens(new ErgoToken(raffleTokenId, 1000), new ErgoToken(serviceTokenId, 1))
        .registers(ErgoValue.of(0L), ErgoValue.of(50L), ErgoValue.of(10L))
        .build()

      val out2 = txB.outBoxBuilder()
        .value(20000000L)
        .contract(new ErgoTreeContract(participantAddress._address.script))
        .build()

      val tx = txB.boxesToSpend(Seq(in1, in2).asJava)
        .fee(1000000)
        .outputs(out1, out2)
        .sendChangeTo(participantAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder()
        .withDLogSecret(participantSecret.bigInteger)
        .build()
      val signedTx = prover.sign(tx)

      println(signedTx.toJson(false))
    }

    {
      // Test Case 3 : Unsuccessful Raffle Ticket Refunding By Project
      // INPUT1 : Raffle Box - INPUT2 : Ticket
      // OUTPUT1: Raffle Box - OUTPUT2: Refunded Box
      val winnerContract = ctx.compileContract(
        ConstantsBuilder.create()
          .build(),
        winnerScript)

      val winnerErgoTree = winnerContract.getErgoTree
      val winnerScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(winnerErgoTree.bytes)

      val ticketContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("deadlineHeight", 50)
          .item("winnerScriptHash", winnerScriptHash)
          .item("ticketPrice", 1000000L)
          .item("projectPubKey", raffleProjectAddress.getPublicKey)
          .build(),
        TicketScript)

      val ticketErgoTree = ticketContract.getErgoTree
      val ticketScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(ticketErgoTree.bytes)

      val raffleContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("tokenId", ErgoId.create(raffleTokenId).getBytes)
          .item("ticketPrice", 1000000L)
          .item("minToRaise", 600000000000L)
          .item("deadlineHeight", 50)
          .item("charityPubKey", charityAddress.getPublicKey)
          .item("servicePubKey", serviceAddress.getPublicKey)
          .item("winnerScriptHash", winnerScriptHash)
          .item("minFee", 1000000L)
          .item("ticketScriptHash", ticketScriptHash)
          .item("oracleNebulaNFT", ErgoId.create(oracleId).getBytes)
          .build(),
        scriptTokenRepo)

      val raffleErgoTree = raffleContract.getErgoTree
      val scriptTokenRepoHash: Digest32 = scorex.crypto.hash.Blake2b256(raffleErgoTree.bytes)

      val txB = ctx.newTxBuilder()

      // R4: Soled Tickets, R5: CharityCoef, R6: ProjectCoef
      val in1 = txB.outBoxBuilder()
        .value(1000000L + 20000000L)
        .contract(raffleContract)
        .tokens(new ErgoToken(raffleTokenId, 980), new ErgoToken(serviceTokenId, 1))
        .registers(ErgoValue.of(20L), ErgoValue.of(50L), ErgoValue.of(10L))
        .build()
        .convertToInputWith("0ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", 0)

      val propByte = new ErgoTreeContract(participantAddress._address.script)
      val in2 = txB.outBoxBuilder()
        .value(1000000L)
        .contract(ticketContract)
        .registers(ErgoValue.of(0L), ErgoValue.of(20L), ErgoValue.of(scriptTokenRepoHash),
          ErgoValue.of(propByte.getErgoTree.bytes))
        .tokens(new ErgoToken(raffleTokenId, 20))
        .build()
        .convertToInputWith("1ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", 0)

      val out1 = txB.outBoxBuilder()
        .value(1000000L)
        .contract(raffleContract)
        .tokens(new ErgoToken(raffleTokenId, 1000), new ErgoToken(serviceTokenId, 1))
        .registers(ErgoValue.of(0L), ErgoValue.of(50L), ErgoValue.of(10L))
        .build()

      val out2 = txB.outBoxBuilder()
        .value(20000000L)
        .contract(new ErgoTreeContract(participantAddress._address.script))
        .build()

      val tx = txB.boxesToSpend(Seq(in1, in2).asJava)
        .fee(1000000)
        .outputs(out1, out2)
        .sendChangeTo(participantAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder()
        .withDLogSecret(raffleProjectSecret.bigInteger)
        .build()
      val signedTx = prover.sign(tx)

      println(signedTx.toJson(false))
    }

    {
      // Test Case 4 : Successful Raffle Constructing 3 Output Results
      // INPUT1 : Raffle Box - INPUT2 : Service Box
      // OUTPUT1: Service Box - OUTPUT2: Charity Box - OUTPUT3: Service Fee Box - OUTPUT4: Winner Box
      val winnerContract = ctx.compileContract(
        ConstantsBuilder.create()
          .build(),
        winnerScript)

      val winnerErgoTree = winnerContract.getErgoTree
      val winnerScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(winnerErgoTree.bytes)

      val ticketContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("deadlineHeight", 50)
          .item("winnerScriptHash", winnerScriptHash)
          .item("ticketPrice", 1000000L)
          .item("projectPubKey", raffleProjectAddress.getPublicKey)
          .build(),
        TicketScript)

      val ticketErgoTree = ticketContract.getErgoTree
      val ticketScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(ticketErgoTree.bytes)

      val raffleContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("tokenId", ErgoId.create(raffleTokenId).getBytes)
          .item("ticketPrice", 1000000L)
          .item("minToRaise", 600000000L)
          .item("deadlineHeight", 50)
          .item("charityPubKey", charityAddress.getPublicKey)
          .item("servicePubKey", serviceAddress.getPublicKey)
          .item("winnerScriptHash", winnerScriptHash)
          .item("minFee", 1000000L)
          .item("ticketScriptHash", ticketScriptHash)
          .item("oracleNebulaNFT", ErgoId.create(oracleId).getBytes)
          .build(),
        scriptTokenRepo)

      val raffleErgoTree = raffleContract.getErgoTree
      val scriptTokenRepoHash: Digest32 = scorex.crypto.hash.Blake2b256(raffleErgoTree.bytes)

      val serviceContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("servicePubKey", serviceAddress.getPublicKey)
          .build(),
        scriptRaffleService)

      val txB = ctx.newTxBuilder()

      // R4: Soled Tickets, R5: CharityCoef, R6: ProjectCoef
      val in1 = txB.outBoxBuilder()
        .value(2000000L + 600000000L)
        .contract(raffleContract)
        .tokens(new ErgoToken(raffleTokenId, 400), new ErgoToken(serviceTokenId, 1))
        .registers(ErgoValue.of(600L), ErgoValue.of(50L), ErgoValue.of(10L))
        .build()
        .convertToInputWith("0ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", 0)

      /* val in2 = txB.outBoxBuilder()
        .value(1000000L)
        .contract(serviceContract)
        .tokens(new ErgoToken(serviceTokenId, 99))
        .build()
        .convertToInputWith("1ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", 0) */

      val out1 = txB.outBoxBuilder()
        .value(1000000L)
        .contract(new ErgoTreeContract(serviceAddress._address.script))
        .tokens(new ErgoToken(serviceTokenId, 1))
        .build()

      val out2 = txB.outBoxBuilder()
        .value(300000000L)
        .contract(new ErgoTreeContract(charityAddress._address.script))
        .build()

      val out3 = txB.outBoxBuilder()
        .value(60000000L)
        .contract(new ErgoTreeContract(serviceAddress._address.script))
        .build()

      val boxes = ctx.getUnspentBoxesFor(Address.create(oracleAddress))
//      val boxes = ctx.getUnspentBoxesFor(Address.create("9hkCHFLdSeomRGsb9xEdq2SAbopFazZWC7aKwdCnJqU9ZRbDKeU"))
      val lastOracleBox = boxes.asScala.toList.headOption.orNull //boxes.asScala.toList.filter(box => box.getTokens.get(0).getId.toString.equals(oracleId)).headOption.orNull
      println(boxes.isEmpty)
      println(boxes)
      val oracleBoxId = lastOracleBox.getId().getBytes

      println(oracleBoxId)
      val winner = ((600 + BigInt(oracleBoxId.slice(0, 15))) % 600).toLong
      println(winner)

      val out4 = txB.outBoxBuilder()
        .value(240000000L)
        .contract(winnerContract)
        .tokens(new ErgoToken(raffleTokenId, 400))
        .registers(ErgoValue.of(winner))
        .build()

      val tx = txB.boxesToSpend(Seq(in1).asJava)
        .fee(1000000)
        .outputs(out1, out2, out3, out4)
        .withDataInputs(Seq(lastOracleBox).toList.asJava)
        .sendChangeTo(raffleProjectAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder()
        .build()
      val signedTx = prover.sign(tx)

      println(signedTx.toJson(false))
    }
    {
      // Test Case 5 : Successful Raffle Winner Box Spending by Project
      // INPUT1 : Winner Box - INPUT2 : Ticket
      // OUTPUT1:
      val winnerContract = ctx.compileContract(
        ConstantsBuilder.create()
          .build(),
        winnerScript)

      val winnerErgoTree = winnerContract.getErgoTree
      val winnerScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(winnerErgoTree.bytes)

      val ticketContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("deadlineHeight", 50)
          .item("winnerScriptHash", winnerScriptHash)
          .item("ticketPrice", 1000000L)
          .item("projectPubKey", raffleProjectAddress.getPublicKey)
          .build(),
        TicketScript)

      val txB = ctx.newTxBuilder()

      val in1 = txB.outBoxBuilder()
        .value(240000000L)
        .contract(winnerContract)
        .tokens(new ErgoToken(raffleTokenId, 400))
        .registers(ErgoValue.of(177L))
        .build()
        .convertToInputWith("0ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", 0)

      val propByte = new ErgoTreeContract(participantAddress._address.script)
      val in2 = txB.outBoxBuilder()
        .value(1000000L)
        .contract(ticketContract)
        .tokens(new ErgoToken(raffleTokenId, 11))
        .registers(ErgoValue.of(172L), ErgoValue.of(11L), ErgoValue.of("random".getBytes()),
          ErgoValue.of(propByte.getErgoTree.bytes))
        .build()
        .convertToInputWith("1ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", 0)

      val out1 = txB.outBoxBuilder()
        .value(240000000L)
        .contract(new ErgoTreeContract(participantAddress._address.script))
        .build()

      val tx = txB.boxesToSpend(Seq(in1, in2).asJava)
        .fee(1000000)
        .outputs(out1)
        .tokensToBurn(new ErgoToken(raffleTokenId, 411))
        .sendChangeTo(raffleProjectAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder()
        .withDLogSecret(raffleProjectSecret.bigInteger)
        .build()
      val signedTx = prover.sign(tx)

      println(signedTx.toJson(false))
    }
  }

  def main(args: Array[String]): Unit = {
    ergoClient.execute((ctx: BlockchainContext) => {
      println(ctx.getHeight)
      makeRaffle(ctx)
    })
  }
}
