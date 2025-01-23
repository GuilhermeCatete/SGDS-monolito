	@SuppressWarnings("rawtypes")
	protected void faturarImovel(Integer anoMesFaturamentoGrupo, int atividade, SistemaParametro sistemaParametro,
			FaturamentoAtivCronRota faturamentoAtivCronRota, Collection colecaoResumoFaturamento, Imovel imovel,
			boolean faturamentoAntecipado, FaturamentoGrupo faturamentoGrupo) throws ControladorException, ErroRepositorioException {

		Integer existeImovelConta = (Integer) getControladorImovel().pesquisarImovelIdComConta(imovel.getId(),
				anoMesFaturamentoGrupo);

		Conta conta = null;

		if (existeImovelConta != null) {

			Collection contas = this.obterConta(existeImovelConta);

			if (contas != null && !contas.isEmpty()) {
				Iterator contasIterator = contas.iterator();

				while (contasIterator.hasNext()) {
					conta = (Conta) contasIterator.next();
				}
			}

		}

		if (existeImovelConta == null || (conta != null && conta.getDebitoCreditoSituacaoAtual() != null
				&& conta.getDebitoCreditoSituacaoAtual().getId().equals(DebitoCreditoSituacao.PRE_FATURADA))) {

			boolean gerarAtividadeGrupoFaturamento = false;

			if (atividade == FaturamentoAtividade.FATURAR_GRUPO.intValue()) {
				gerarAtividadeGrupoFaturamento = true;
			} else if (atividade == FaturamentoAtividade.SIMULAR_FATURAMENTO.intValue()) {
				gerarAtividadeGrupoFaturamento = false;
			}
			this.determinarFaturamentoImovel(imovel, gerarAtividadeGrupoFaturamento, faturamentoAtivCronRota,
					colecaoResumoFaturamento, sistemaParametro, faturamentoAntecipado, anoMesFaturamentoGrupo,
					faturamentoGrupo);
		}
	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void determinarFaturamentoImovel(Imovel imovel, boolean gerarAtividadeGrupoFaturamento,
			FaturamentoAtivCronRota faturamentoAtivCronRota, Collection colecaoResumoFaturamento,
			SistemaParametro sistemaParametro, boolean faturamentoAntecipado, Integer anoMesFaturamentoGrupo,
			FaturamentoGrupo faturamentoGrupo) throws ControladorException, ErroRepositorioException {

		Collection colecaoCategorias = getControladorImovel().obterQuantidadeEconomiasCategoria(imovel);
		Collection colecaoCategoriaOUSubcategoria = getControladorImovel()
				.obterColecaoCategoriaOuSubcategoriaDoImovel(imovel);

		DeterminarValoresFaturamentoAguaEsgotoHelper helperValoresAguaEsgoto = new DeterminarValoresFaturamentoAguaEsgotoHelper();

		LigacaoTipo ligacaoTipoAgua = new LigacaoTipo(LigacaoTipo.LIGACAO_AGUA);
		LigacaoTipo ligacaoTipoEsgoto = new LigacaoTipo(LigacaoTipo.LIGACAO_ESGOTO);

		ConsumoHistorico consumoHistoricoAgua = this.getControladorMicromedicao()
				.obterConsumoHistoricoMedicaoIndividualizada(imovel, ligacaoTipoAgua, anoMesFaturamentoGrupo);

		Integer consumoAgua = null;
		ConsumoTipo consumoTipoAgua = null;

		if (consumoHistoricoAgua != null) {
			consumoAgua = consumoHistoricoAgua.getNumeroConsumoFaturadoMes();
			consumoTipoAgua = consumoHistoricoAgua.getConsumoTipo();
		}

		ConsumoHistorico consumoHistoricoEsgoto = this.getControladorMicromedicao()
				.obterConsumoHistoricoMedicaoIndividualizada(imovel, ligacaoTipoEsgoto, anoMesFaturamentoGrupo);

		Integer consumoEsgoto = null;
		ConsumoTipo consumoTipoEsgoto = null;

		if (consumoHistoricoEsgoto != null) {
			consumoEsgoto = consumoHistoricoEsgoto.getNumeroConsumoFaturadoMes();
			consumoTipoEsgoto = consumoHistoricoEsgoto.getConsumoTipo();
		}
		if (permiteFaturarSituacaoEspecialFaturamento(imovel, anoMesFaturamentoGrupo)) {

			if (this.permiteFaturamentoParaAgua(imovel.getLigacaoAguaSituacao(), consumoAgua, consumoTipoAgua)
					|| this.permiteFaturamentoParaEsgoto(imovel.getLigacaoEsgotoSituacao(), consumoEsgoto,
							consumoTipoEsgoto)) {

				helperValoresAguaEsgoto = this.determinarValoresFaturamento(imovel, anoMesFaturamentoGrupo,
						colecaoCategoriaOUSubcategoria, faturamentoGrupo, consumoHistoricoAgua, consumoHistoricoEsgoto,
						false);
			}

			boolean gerarConta = false;

			if (imovel.useNovaChecagemGerarConta() || imovel.getImovelCondominio() != null) {
				boolean imovelSemConsumo = helperValoresAguaEsgoto.imovelSemConsumo();

				gerarConta = getControladorAnaliseGeracaoConta().verificarGeracaoConta(imovelSemConsumo,
						anoMesFaturamentoGrupo, imovel);
			} else {
				gerarConta = this.verificarNaoGeracaoConta(imovel, helperValoresAguaEsgoto.getValorTotalAgua(),
						helperValoresAguaEsgoto.getValorTotalEsgoto(), anoMesFaturamentoGrupo, false);
			}
			if (gerarConta) {

				boolean preFaturamento = false;

				GerarDebitoCobradoHelper gerarDebitoCobradoHelper = this.gerarDebitoCobrado(imovel,
						anoMesFaturamentoGrupo, sistemaParametro, gerarAtividadeGrupoFaturamento);

				GerarCreditoRealizadoHelper gerarCreditoRealizadoHelper = this.gerarCreditoRealizado(imovel,
						anoMesFaturamentoGrupo, helperValoresAguaEsgoto, gerarDebitoCobradoHelper.getValorTotalDebito(),
						gerarAtividadeGrupoFaturamento, preFaturamento);

				if (gerarAtividadeGrupoFaturamento) {

					GerarImpostosDeduzidosContaHelper gerarImpostosDeduzidosContaHelper = this
							.gerarImpostosDeduzidosConta(imovel.getId(), anoMesFaturamentoGrupo,
									helperValoresAguaEsgoto.getValorTotalAgua(),
									helperValoresAguaEsgoto.getValorTotalEsgoto(),
									gerarDebitoCobradoHelper.getValorTotalDebito(),
									gerarCreditoRealizadoHelper.getValorTotalCredito(), preFaturamento);

					gerarDebitoCobradoHelper.setGerarImpostosDeduzidosContaHelper(gerarImpostosDeduzidosContaHelper);
					Conta conta = this.gerarConta(imovel, anoMesFaturamentoGrupo, sistemaParametro,
							faturamentoAtivCronRota, helperValoresAguaEsgoto, gerarDebitoCobradoHelper,
							gerarCreditoRealizadoHelper, gerarImpostosDeduzidosContaHelper, faturamentoGrupo,
							faturamentoAntecipado, preFaturamento);

					GerarContaCategoriaHelper gerarContaCategoriaHelper = this.gerarContaCategoria(conta,
							colecaoCategoriaOUSubcategoria,
							helperValoresAguaEsgoto.getColecaoCalcularValoresAguaEsgotoHelper(), sistemaParametro);

					// INSERINDO CONTA_CATEGORIA NA BASE
					if (gerarContaCategoriaHelper.getColecaoContaCategoria() != null
							&& !gerarContaCategoriaHelper.getColecaoContaCategoria().isEmpty()) {
						this.getControladorBatch()
								.inserirColecaoObjetoParaBatch(gerarContaCategoriaHelper.getColecaoContaCategoria());
					}

					// INSERINDO CONTA_CATEGORIA_CONSUMO_FAIXA
					if (gerarContaCategoriaHelper.getColecaoContaCategoriaConsumoFaixa() != null
							&& !gerarContaCategoriaHelper.getColecaoContaCategoriaConsumoFaixa().isEmpty()) {

						this.getControladorBatch().inserirColecaoObjetoParaBatch(
								gerarContaCategoriaHelper.getColecaoContaCategoriaConsumoFaixa());
					}

					this.inserirClienteConta(conta, imovel);
					this.inserirContaImpostosDeduzidos(conta, gerarImpostosDeduzidosContaHelper);
					this.inserirDebitoCobrado(gerarDebitoCobradoHelper.getMapDebitosCobrados(), conta);

					this.atualizarDebitoACobrarFaturamento(gerarDebitoCobradoHelper.getColecaoDebitoACobrar());

					this.inserirCreditoRealizado(gerarCreditoRealizadoHelper.getMapCreditoRealizado(), conta);

					this.atualizarCreditoARealizar(gerarCreditoRealizadoHelper.getColecaoCreditoARealizar());

					this.gerarContaImpressao(conta, faturamentoGrupo, imovel, faturamentoAtivCronRota.getRota());

					if (imovel.getIndicadorDebitoConta().equals(ConstantesSistema.SIM)
							&& conta.getContaMotivoRevisao() == null) {
						conta.setImovel(imovel);
						conta.setFaturamentoGrupo(faturamentoGrupo);

						this.gerarMovimentoDebitoAutomatico(conta);
					}

					try {
						repositorioFaturamento.concluirFaturamentoConta(conta.getId());
					} catch (ErroRepositorioException e) {
						throw new ControladorException("Erro ao concluir etapa de faturamento", e);
					}
					
					/* Metodo comentado, esperando pra entrar em produção para GERAR QRCODE
					 * Paulo Almeida -- 12/12/2024
						String cpfCnpj = getControladorCliente().consultarCpfCnpjClienteResponsavel(imovel.getIdImovel());
						BigDecimal valorOriginal = BigDecimal.valueOf(Double.valueOf(conta.getValorTotalConta()));
										  
					if (isImovelValidoParaGerarQrCode(imovel, cpfCnpj, valorOriginal)) {
						getControladorBanpara().gerarQrCode(conta.getId());
					}*/
					
				}

				if (!gerarAtividadeGrupoFaturamento) {
					GerarImpostosDeduzidosContaHelper gerarImpostosDeduzidosContaHelper = this
							.gerarImpostosDeduzidosConta(imovel.getId(), anoMesFaturamentoGrupo,
									helperValoresAguaEsgoto.getValorTotalAgua(),
									helperValoresAguaEsgoto.getValorTotalEsgoto(),
									gerarDebitoCobradoHelper.getValorTotalDebito(),
									gerarCreditoRealizadoHelper.getValorTotalCredito(), preFaturamento);

					gerarDebitoCobradoHelper.setGerarImpostosDeduzidosContaHelper(gerarImpostosDeduzidosContaHelper);

					Integer anoMesReferenciaResumoFaturamento = null;

					if (faturamentoAntecipado) {
						anoMesReferenciaResumoFaturamento = anoMesFaturamentoGrupo;
					}

					this.gerarResumoFaturamentoSimulacao(colecaoCategorias,
							helperValoresAguaEsgoto.getColecaoCalcularValoresAguaEsgotoHelper(),
							gerarDebitoCobradoHelper, gerarCreditoRealizadoHelper, colecaoResumoFaturamento, imovel,
							gerarAtividadeGrupoFaturamento, faturamentoAtivCronRota, faturamentoGrupo,
							anoMesReferenciaResumoFaturamento, false);
				}
			}
			
		}
	}


		private boolean permiteFaturarSituacaoEspecialFaturamento(Imovel imovel, Integer anoMesFaturamento)
			throws ControladorException {
		boolean faturar = true;

		// Verificar se Ã© para faturar pela situaÃ§Ã£o especial de faturamento
		FiltroFaturamentoSituacaoHistorico filtroFaturamentoSituacaoHistorico = new FiltroFaturamentoSituacaoHistorico();
		filtroFaturamentoSituacaoHistorico
				.adicionarParametro(new ParametroSimples(FiltroFaturamentoSituacaoHistorico.ID_IMOVEL, imovel.getId()));
		filtroFaturamentoSituacaoHistorico
				.adicionarParametro(new ParametroNulo(FiltroFaturamentoSituacaoHistorico.ANO_MES_FATURAMENTO_RETIRADA));
		Collection<FaturamentoSituacaoHistorico> colFiltroFaturamentoSituacaoHistorico = this.getControladorUtil()
				.pesquisar(filtroFaturamentoSituacaoHistorico, FaturamentoSituacaoHistorico.class.getName());

		FaturamentoSituacaoHistorico faturamentoSituacaoHistorico = (FaturamentoSituacaoHistorico) Util
				.retonarObjetoDeColecao(colFiltroFaturamentoSituacaoHistorico);

		if (imovel.getFaturamentoSituacaoTipo() != null && !imovel.getFaturamentoSituacaoTipo().equals("")) {

			if ((faturamentoSituacaoHistorico != null
					&& anoMesFaturamento >= faturamentoSituacaoHistorico.getAnoMesFaturamentoSituacaoInicio()
					&& anoMesFaturamento <= faturamentoSituacaoHistorico.getAnoMesFaturamentoSituacaoFim())
					&& (imovel.getFaturamentoSituacaoTipo() != null
							&& (imovel.getFaturamentoSituacaoTipo().getIndicadorParalisacaoFaturamento().intValue() == 1
									&& imovel.getFaturamentoSituacaoTipo()
											.getId() != FaturamentoSituacaoTipo.SITUACAO_ESPECIAL_BOLSA_AGUA)
							&& imovel.getFaturamentoSituacaoTipo().getIndicadorValidoAgua().intValue() == 1)) {
				faturar = false;
			}
		}
		return faturar;
	}

		public boolean permiteFaturamentoParaAgua(LigacaoAguaSituacao ligacaoAguaSituacao, Integer consumoAgua,
			ConsumoTipo consumoTipo) throws ControladorException {

		boolean permiteFaturar = false;

		// LIGACAO_TIPO_AGUA
		LigacaoTipo ligacaoTipoAgua = new LigacaoTipo();
		ligacaoTipoAgua.setId(LigacaoTipo.LIGACAO_AGUA);

		/*
		 * Selecionar os imÃ³veis que farÃ£o parte do faturamento de acordo com o
		 * INDICADOR_FATURAMENTO_SITUACAO e CONSUMO_MINIMO_FATURAMENTO que se encontra
		 * na situaÃ§Ã£o da ligaÃ§Ã£o e Ã¡gua e esgoto do imÃ³vel.
		 */
		if (ligacaoAguaSituacao.getIndicadorFaturamentoSituacao().equals(LigacaoAguaSituacao.FATURAMENTO_ATIVO)
				&& ligacaoAguaSituacao.getConsumoMinimoFaturamento().intValue() <= 0) {

			permiteFaturar = true;
		} else if (ligacaoAguaSituacao.getIndicadorFaturamentoSituacao().equals(LigacaoAguaSituacao.FATURAMENTO_ATIVO)
				&& ligacaoAguaSituacao.getConsumoMinimoFaturamento().intValue() > 0) {

			/*
			 * Para faturar: O valor do consumo mÃ­nimo da ligaÃ§Ã£o tem que ser menor ou
			 * igual ao valor do consumo do imÃ³vel e o tipo de consumo esteja associado a
			 * situaÃ§Ã£o da ligaÃ§Ã£o do imÃ³vel.
			 */
			if (consumoAgua != null
					&& ligacaoAguaSituacao.getConsumoMinimoFaturamento().intValue() <= consumoAgua.intValue()) {

				if (consumoTipo != null) {

					LigacaoAguaSituacaoConsumoTipo ligacaoAguaSituacaoConsumoTipo = this.getControladorLigacaoAgua()
							.pesquisarLigacaoAguaSituacaoConsumoTipo(ligacaoAguaSituacao.getId(), consumoTipo.getId());

					if (ligacaoAguaSituacaoConsumoTipo != null) {
						permiteFaturar = true;
					}
				} else {

					// EstÃ¡ situaÃ§Ã£o irÃ¡ acontecer apenas para as
					// funcionalidades do online
					permiteFaturar = true;
				}
			}
		}

		return permiteFaturar;
	}

	public boolean permiteFaturamentoParaEsgoto(LigacaoEsgotoSituacao ligacaoEsgotoSituacao, Integer consumoEsgoto,
			ConsumoTipo consumoTipo) throws ControladorException {

		boolean permiteFaturar = false;

		// LIGACAO_TIPO_ESGOTO
		LigacaoTipo ligacaoTipoEsgoto = new LigacaoTipo();
		ligacaoTipoEsgoto.setId(LigacaoTipo.LIGACAO_ESGOTO);

		if (ligacaoEsgotoSituacao != null) {

			if (ligacaoEsgotoSituacao.getIndicadorFaturamentoSituacao().equals(LigacaoEsgotoSituacao.FATURAMENTO_ATIVO)
					&& ligacaoEsgotoSituacao.getVolumeMinimoFaturamento().intValue() <= 0) {

				permiteFaturar = true;
			} else if (ligacaoEsgotoSituacao.getIndicadorFaturamentoSituacao()
					.equals(LigacaoEsgotoSituacao.FATURAMENTO_ATIVO)
					&& ligacaoEsgotoSituacao.getVolumeMinimoFaturamento().intValue() > 0) {

				if (consumoEsgoto != null
						&& ligacaoEsgotoSituacao.getVolumeMinimoFaturamento().intValue() <= consumoEsgoto.intValue()) {

					if (consumoTipo != null) {

						LigacaoEsgotoSituacaoConsumoTipo ligacaoEsgotoSituacaoConsumoTipo = this
								.getControladorLigacaoEsgoto().pesquisarLigacaoEsgotoSituacaoConsumoTipo(
										ligacaoEsgotoSituacao.getId(), consumoTipo.getId());

						if (ligacaoEsgotoSituacaoConsumoTipo != null) {
							permiteFaturar = true;
						}
					} else {

						// EstÃ¡ situaÃ§Ã£o irÃ¡ acontecer apenas para as
						// funcionalidades do online
						permiteFaturar = true;
					}
				}
			}
		}

		return permiteFaturar;
	}

		public DeterminarValoresFaturamentoAguaEsgotoHelper determinarValoresFaturamento(Imovel imovel,
			Integer anoMesFaturamento, Collection colecaoCategoriasOUSubCategorias, FaturamentoGrupo faturamentoGrupo,
			ConsumoHistorico consumoHistoricoAgua, ConsumoHistorico consumoHistoricoEsgoto,
			boolean isImpressaoSimultanea) throws ControladorException {

		DeterminarValoresFaturamentoAguaEsgotoHelper helper = determinarValoresFaturamentoAguaEsgoto(imovel,
				anoMesFaturamento, colecaoCategoriasOUSubCategorias, faturamentoGrupo, consumoHistoricoAgua,
				consumoHistoricoEsgoto);

		return helper;

	}

		@SuppressWarnings("rawtypes")
	public boolean verificarNaoGeracaoConta(Imovel imovel, BigDecimal valorTotalAgua, BigDecimal valorTotalEsgoto,
			int anoMesFaturamentoGrupo, boolean isPreFaturamento) throws ControladorException {

		boolean retorno = true;
		boolean primeiraCondicaoNaoGerarConta = false;
		boolean segundaCondicaoNaoGerarConta = false;

		/*
		 * AlteraÃ§Ã£o para gerar a rota com imÃ³veis ativos ou inativos com dÃ©bitos
		 */
		// 1.1 Caso o valor total da Ã¡gua e o valor total do esgoto seja igual
		// a
		// zero. Satisfaz a primeira condiÃ§Ã£o.
		if ((valorTotalAgua.compareTo(ConstantesSistema.VALOR_ZERO) == 0
				&& valorTotalEsgoto.compareTo(ConstantesSistema.VALOR_ZERO) == 0)
				|| (valorTotalAgua.compareTo(ConstantesSistema.VALOR_ZERO) != 0
						&& valorTotalEsgoto.compareTo(ConstantesSistema.VALOR_ZERO) != 0
						&& !imovel.getLigacaoAguaSituacao().getId().equals(LigacaoAguaSituacao.LIGADO)
						&& !imovel.getLigacaoEsgotoSituacao().getId().equals(LigacaoEsgotoSituacao.LIGADO)
						&& (isPreFaturamento && imovel.getImovelCondominio() == null))) {

			if (!imovel.isImovelMicroCondominio()) {
				logger.info(imovel.getId() + " NAO GEROU CONTA : primeiraCondicaoNaoGerarConta");
				primeiraCondicaoNaoGerarConta = true;
			}
		}

		Collection colecaoDebitosACobrar = null;
		Collection colecaoCreditosARealizar = null;

		/*
		 * Colocado por Raphael Rossiter em 20/03/2007
		 */
		colecaoDebitosACobrar = this.obterDebitoACobrarImovel(imovel.getId(), DebitoCreditoSituacao.NORMAL,
				anoMesFaturamentoGrupo);

		// 1.2.1 Caso nÃ£o existam Debitos a Cobrar.
		if (colecaoDebitosACobrar == null || colecaoDebitosACobrar.isEmpty()) {
			logger.info(imovel.getId() + " NAO GEROU CONTA : segundaCondicaoNaoGerarConta");
			segundaCondicaoNaoGerarConta = true;
		}
		/**
		 *
		 * 
		 * @author Adriana Muniz date: 28/06/2012
		 * 
		 *         CondiÃ§Ã£o para nÃ£o gerar conta para imÃ³vel na situaÃ§Ã£o especial
		 *         como o indicador de paralisaÃ§Ã£o do faturamento igual a 1
		 */
		else if (imovel.getFaturamentoSituacaoTipo() != null && imovel.getFaturamentoSituacaoTipo()
				.getIndicadorParalisacaoFaturamento().equals(ConstantesSistema.SIM)) {
			logger.info(imovel.getId() + " NAO GEROU CONTA : segundaCondicaoNaoGerarConta 2");
			segundaCondicaoNaoGerarConta = true;
		} else {
			// 1.2.2 OU, Caso existam Debitos a Cobrar E existam Pagamentos
			boolean existeDebitoSemPagamento = false;
			Iterator iColecaoDebitosACobrar = colecaoDebitosACobrar.iterator();

			while (iColecaoDebitosACobrar.hasNext() && !existeDebitoSemPagamento) {
				DebitoACobrar debitoACobrar = (DebitoACobrar) iColecaoDebitosACobrar.next();
				// Para cada DebitoACobrar verificamos de existe pagamento
				FiltroPagamento filtroPagamento = new FiltroPagamento();
				filtroPagamento.adicionarParametro(
						new ParametroSimples(FiltroPagamento.DEBITO_A_COBRAR, debitoACobrar.getId()));
				Collection colecaoPagamentos = getControladorUtil().pesquisar(filtroPagamento,
						Pagamento.class.getName());
				if (colecaoPagamentos == null || colecaoPagamentos.isEmpty()) {
					existeDebitoSemPagamento = true;
				}
			}
			/**************************************************************/
			if (!existeDebitoSemPagamento) {
				logger.info(imovel.getId() + " NAO GEROU CONTA : segundaCondicaoNaoGerarConta 3");
				segundaCondicaoNaoGerarConta = true;
			} else {
				// 1.2.3 OU, Caso existam Debitos a Cobrar E NAO existam
				// Pagamentos
				try {
					SistemaParametro sistemaParametro = getControladorUtil().pesquisarParametrosDoSistema();
					colecaoCreditosARealizar = repositorioFaturamento.pesquisarCreditoARealizar(imovel.getId(),
							DebitoCreditoSituacao.NORMAL, anoMesFaturamentoGrupo, sistemaParametro);

				} catch (ErroRepositorioException ex) {
					sessionContext.setRollbackOnly();
					throw new ControladorException("erro.sistema", ex);
				}

				boolean achouDevolucao = false;
				if (colecaoCreditosARealizar != null && !colecaoCreditosARealizar.isEmpty()) {
					Iterator iteratorColecaoCreditosARealizar = colecaoCreditosARealizar.iterator();
					CreditoARealizar creditoARealizar = null;

					while (iteratorColecaoCreditosARealizar.hasNext()) {
						Object[] arrayCreditosACobrar = (Object[]) iteratorColecaoCreditosARealizar.next();
						creditoARealizar = new CreditoARealizar();
						creditoARealizar.setId((Integer) arrayCreditosACobrar[0]);

						// Para cada CreditoARealizar verificamos se existe
						// Devolucao
						FiltroDevolucao filtroDevolucao = new FiltroDevolucao();
						filtroDevolucao.adicionarParametro(
								new ParametroSimples(FiltroDevolucao.CREDITO_A_REALIZAR_ID, creditoARealizar.getId()));
						Collection colecaoDevolucao = getControladorUtil().pesquisar(filtroDevolucao,
								Devolucao.class.getName());

						if (colecaoDevolucao != null && !colecaoDevolucao.isEmpty()) {
							achouDevolucao = true;
						}
					}
				}

				// 1.2.3.1.1 Caso NAO existam Credito a Realizar OU
				// Caso exista Credito a Realizar e existam Devolucoes para os
				// Creditos a Realizar.
				if ((colecaoCreditosARealizar == null || colecaoCreditosARealizar.isEmpty()) || (achouDevolucao)) {
					Iterator iteratorColecaoDebitosACobrar = colecaoDebitosACobrar.iterator();
					DebitoACobrar debitoACobrar = null;
					DebitoTipo debitoTipo = null;
					// SubCondicao3
					logger.info(imovel.getId() + " NAO GEROU CONTA : segundaCondicaoNaoGerarConta 4");
					segundaCondicaoNaoGerarConta = true;

					while (iteratorColecaoDebitosACobrar.hasNext()) {
						debitoACobrar = (DebitoACobrar) iteratorColecaoDebitosACobrar.next();

						try {
							debitoTipo = repositorioFaturamento.getDebitoTipo(debitoACobrar.getDebitoTipo().getId());
						} catch (ErroRepositorioException ex) {
							sessionContext.setRollbackOnly();
							throw new ControladorException("erro.sistema", ex);
						}

						// 1.2.3.1.1.1.1 Caso os Debitos a Cobrar sejam todos
						// correspondentes
						// a Tipo de Debito com o indicador de geracao de conta
						// igual a NAO.
						if (debitoTipo.getIndicadorGeracaoConta().shortValue() != 2) {
							logger.info(
									imovel.getId() + " NAO GEROU CONTA : segundaCondicaoNaoGerarConta false");
							segundaCondicaoNaoGerarConta = false;
						}
					}
				} else {
					logger.info(imovel.getId() + " NAO GEROU CONTA : segundaCondicaoNaoGerarConta false 2");
					segundaCondicaoNaoGerarConta = false;
				}
			}
		}

		if (colecaoDebitosACobrar != null) {
			colecaoDebitosACobrar.clear();
			colecaoDebitosACobrar = null;
		}

		if (colecaoCreditosARealizar != null) {
			colecaoCreditosARealizar.clear();
			colecaoCreditosARealizar = null;
		}

		/*
		 * Caso as duas condiÃ§Ãµes sejam verdadeiras nÃ£o gera a conta.
		 */
		if (primeiraCondicaoNaoGerarConta && segundaCondicaoNaoGerarConta) {
			retorno = false;
		}

		return retorno;
	}

		public GerarDebitoCobradoHelper gerarDebitoCobrado(Imovel imovel, Integer anoMesFaturamento,
			SistemaParametro sistemaParametro, boolean gerarAtividadeGrupoFaturamento) throws ControladorException {

		GerarDebitoCobradoHelper helper = new GerarDebitoCobradoHelper();

		// Pesquisa os dÃ©bitos a cobrar do imÃ³vel.
		Collection colecaoDebitosACobrar = this.obterDebitoACobrarImovel(imovel.getId(), DebitoCreditoSituacao.NORMAL,
				anoMesFaturamento);

		BigDecimal valorTotalDebitos = ConstantesSistema.VALOR_ZERO;
		List colecaoDebitosACobrarUpdate = new ArrayList();
		Collection colecaoDebitosCobradoCategoria = null;

		// Cria o map para armazenar os dÃ©bitos cobrados junto com os dÃ©bitos
		// cobrados por categoria.
		Map<DebitoCobrado, Collection<DebitoCobradoCategoria>> mapDebitosCobrados = null;
		// Cria o map para armazenar os dÃ©bitos a cobradar junto com os valores
		// por tipo dÃ©bito.
		Map<DebitoTipo, BigDecimal> mapValoresPorTipoDebito = null;

		/*
		 * Caso o imÃ³vel tenha dÃ©bitos a cobrar gera os dÃ©bitos cobrados
		 */
		if (colecaoDebitosACobrar != null && !colecaoDebitosACobrar.isEmpty()) {

			Iterator iteratorColecaoDebitosACobrar = colecaoDebitosACobrar.iterator();

			mapDebitosCobrados = new HashMap();
			mapValoresPorTipoDebito = new HashMap<DebitoTipo, BigDecimal>();
			DebitoACobrar debitoACobrar = null;
			BigDecimal valorPrestacao = null;

			// LAÃO PARA GERAR OS DÃBITOS COBRADOS E OS DÃBITOS COBRADOS POR
			// CATEGORIA
			while (iteratorColecaoDebitosACobrar.hasNext()) {

				debitoACobrar = (DebitoACobrar) iteratorColecaoDebitosACobrar.next();

				// Calcula o valor da prestaÃ§Ã£o
				valorPrestacao = debitoACobrar.getValorDebito()
						.divide(new BigDecimal(debitoACobrar.getNumeroPrestacaoDebito()), 2, BigDecimal.ROUND_DOWN);

				/*
				 * Alterado por Vivianne Sousa em 20/12/2007 - Analista: Adriano criaÃ§Ã£o do
				 * bonus para parcelamento com RD especial
				 */
				Short numeroParcelaBonus = 0;
				if (debitoACobrar.getNumeroParcelaBonus() != null) {
					numeroParcelaBonus = debitoACobrar.getNumeroParcelaBonus();
				}

				// Caso seja a Ãºltima prestaÃ§Ã£o
				if (debitoACobrar.getNumeroPrestacaoCobradas() == ((debitoACobrar.getNumeroPrestacaoDebito()
						- numeroParcelaBonus) - 1)) {

					// ObtÃ©m o nÃºmero de prestaÃ§Ã£o dÃ©bito
					BigDecimal numeroPrestacaoDebito = new BigDecimal(debitoACobrar.getNumeroPrestacaoDebito());

					// Mutiplica o (valor da prestaÃ§Ã£o * nÃºmero da
					// prestaÃ§Ã£o
					// debito) - numeroParcelaBonus

					BigDecimal multiplicacao = valorPrestacao.multiply(numeroPrestacaoDebito).setScale(2);

					// Subtrai o valor do dÃ©bito pelo resultado da
					// multiplicaÃ§Ã£o
					BigDecimal parte1 = debitoACobrar.getValorDebito().subtract(multiplicacao).setScale(2);

					// Calcula o valor da prestaÃ§Ã£o
					valorPrestacao = valorPrestacao.add(parte1).setScale(2);
				}

				// Acumula o valor da prestaÃ§Ã£o no valor total dos debitos
				valorTotalDebitos = valorTotalDebitos.add(valorPrestacao);

				// Se a atividade Ã© faturar grupo de faturamento gera os
				// dÃ©bitos
				// cobrados
				if (gerarAtividadeGrupoFaturamento) {

					// GERANDO O DÃBITO COBRADO
					// -----------------------------------------------------------------------------------------
					DebitoCobrado debitoCobrado = new DebitoCobrado();

					DebitoACobrarGeral debitoACobrarGeral = new DebitoACobrarGeral();
					debitoACobrarGeral.setId(debitoACobrar.getId());

					debitoCobrado.setDebitoACobrarGeral(debitoACobrarGeral);
					debitoCobrado.setDebitoTipo(debitoACobrar.getDebitoTipo());
					debitoCobrado.setUltimaAlteracao(new Date());
					debitoCobrado.setLancamentoItemContabil(debitoACobrar.getLancamentoItemContabil());
					debitoCobrado.setLocalidade(debitoACobrar.getLocalidade());
					debitoCobrado.setQuadra(debitoACobrar.getQuadra());
					debitoCobrado.setCodigoSetorComercial(debitoACobrar.getCodigoSetorComercial());
					debitoCobrado.setNumeroQuadra(debitoACobrar.getNumeroQuadra());
					debitoCobrado.setNumeroLote(debitoACobrar.getNumeroLote());
					debitoCobrado.setNumeroSubLote(debitoACobrar.getNumeroSubLote());
					debitoCobrado.setAnoMesReferenciaDebito(debitoACobrar.getAnoMesReferenciaDebito());
					debitoCobrado.setAnoMesCobrancaDebito(debitoACobrar.getAnoMesCobrancaDebito());
					debitoCobrado.setValorPrestacao(valorPrestacao);
					debitoCobrado.setNumeroPrestacao(debitoACobrar.getNumeroPrestacaoDebito());
					debitoCobrado.setFinanciamentoTipo(debitoACobrar.getFinanciamentoTipo());

					debitoCobrado.setNumeroParcelaBonus(numeroParcelaBonus);

					// Incrementa o nÂº de prestaÃ§Ã£o cobradas
					int numeroPrestacaoCobradas = debitoACobrar.getNumeroPrestacaoCobradas() + 1;
					debitoCobrado.setNumeroPrestacaoDebito((short) numeroPrestacaoCobradas);
					// ----------------------------------------------------------------------------------------
					logger.info("DEBITO A REALIZAR CATEGORIA DO DEBITO: " + debitoACobrar.getId());
					// Pesquisa os debitos a cobrar categoria do debito a cobrar
					Collection colecaoDebitoACobrarCategoria = this.obterDebitoACobrarCategoria(debitoACobrar.getId());

					// Carregando as categorias do debitoACobrarCategoria
					Iterator colecaoDebitoACobrarCategoriaIterator = colecaoDebitoACobrarCategoria.iterator();

					Collection colecaoCategoriasObterValor = new ArrayList();

					while (colecaoDebitoACobrarCategoriaIterator.hasNext()) {

						DebitoACobrarCategoria debitoACobrarCategoria = (DebitoACobrarCategoria) colecaoDebitoACobrarCategoriaIterator
								.next();

						Categoria categoria = new Categoria();
						categoria.setId(debitoACobrarCategoria.getCategoria().getId());
						categoria.setQuantidadeEconomiasCategoria(debitoACobrarCategoria.getQuantidadeEconomia());

						colecaoCategoriasObterValor.add(categoria);
					}

					// [UC0185] Obter Valor por Categoria
					Collection colecaoCategoriasCalculadasValor = getControladorImovel()
							.obterValorPorCategoria(colecaoCategoriasObterValor, valorPrestacao);

					// GERANDO O DÃBITO COBRADO CATEGORIA
					// -------------------------------------------------------------------------------------------
					DebitoCobradoCategoria debitoCobradoCategoria = null;
					DebitoCobradoCategoriaPK debitoCobradoCategoriaPK = null;

					Iterator colecaoCategoriasCalculadasValorIterator = colecaoCategoriasCalculadasValor.iterator();
					Iterator colecaoCategoriasObterValorIterator = colecaoCategoriasObterValor.iterator();

					colecaoDebitosCobradoCategoria = new ArrayList();

					// LAÃO PARA GERAR OS DÃBITOS COBRADOS POR CATEGORIA
					while (colecaoCategoriasCalculadasValorIterator.hasNext()
							&& colecaoCategoriasObterValorIterator.hasNext()) {

						// Cria o dÃ©bito a cobrar por categoria e adiciona a
						// coleÃ§Ã£o.
						debitoCobradoCategoria = new DebitoCobradoCategoria();

						// VALOR POR CATEGORIA
						BigDecimal valorPorCategoria = (BigDecimal) colecaoCategoriasCalculadasValorIterator.next();

						// CATEGORIA
						Categoria categoria = (Categoria) colecaoCategoriasObterValorIterator.next();

						debitoCobradoCategoria.setValorCategoria(valorPorCategoria);

						debitoCobradoCategoriaPK = new DebitoCobradoCategoriaPK();
						debitoCobradoCategoriaPK.setCategoriaId(categoria.getId());
						debitoCobradoCategoriaPK.setDebitoCobradoId(debitoCobrado.getId());

						debitoCobradoCategoria.setComp_id(debitoCobradoCategoriaPK);
						debitoCobradoCategoria.setDebitoCobrado(debitoCobrado);
						debitoCobradoCategoria.setCategoria(categoria);
						debitoCobradoCategoria.setQuantidadeEconomia(categoria.getQuantidadeEconomiasCategoria());

						// INSERINDO O debitoCobradoCategoria NA COLEÃÃO DE
						// RETORNO
						colecaoDebitosCobradoCategoria.add(debitoCobradoCategoria);
					}

					/*
					 * Adiciona no map o relacionamento entre o dÃ©bito a cobrar e os dÃ©bitos a
					 * cobrar por categoria.
					 */
					mapDebitosCobrados.put(debitoCobrado, colecaoDebitosCobradoCategoria);

					/*
					 * Adiciona no map o relacionamento entre o dÃ©bito cobrado e o auto de
					 * infraÃ§Ã£o
					 */

					/**
					 * AlteraÃ§Ã£o feita por Bruno Barros dia 08 de Janeiro de 2009 Solicitante:
					 * Nelson Carvalho DescriÃ§Ã£o da solicitaÃ§Ã£o: NÃ£o mais gerar o vinculo de
					 * debito cobrado a um auto de infraÃ§Ã£o quando o dÃ©bito a cobrar de origem
					 * estiver vinculado ao mesmo.
					 */

					/*
					 * Atualiza o nÂº de prestaÃ§Ãµes cobradas do dÃ©bito a cobrar e adicona o
					 * objeto a coleÃ§Ã£o de dÃ©bitos a cobrar que vai ser atualizados.
					 */
					debitoACobrar.setNumeroPrestacaoCobradas(
							new Integer(debitoACobrar.getNumeroPrestacaoCobradas() + 1).shortValue());

					// anoMes da prestaÃ§Ã£o serÃ¡ o anaMes de referÃªncia da
					// conta
					debitoACobrar.setAnoMesReferenciaPrestacao(anoMesFaturamento);

					// INSERINDO O debitoACobrar NA COLEÃÃO DE RETORNO
					colecaoDebitosACobrarUpdate.add(debitoACobrar);

				} // fim se atividade grupo faturamento

				/*
				 * Desenvolvedor: Hugo Amorim Analista:Jeferson Pedrosa Data: 29/07/2010
				 * 
				 * [CRC4457] Colecionar os valores que compÃµem os totais de dÃ©bito e crÃ©ditos
				 * nas tabelas resumo_faturamento_simulado_detalhe_debito e
				 * resumo_faturamento_simulado_detalhe_credito respectivamente.
				 */
				// Verifica se debito a cobrar jÃ¡ foi inserido, caso sim
				// acumala os valores.
				if (mapValoresPorTipoDebito.containsKey(debitoACobrar.getDebitoTipo())) {
					BigDecimal valor = mapValoresPorTipoDebito.get(debitoACobrar.getDebitoTipo());
					mapValoresPorTipoDebito.put(debitoACobrar.getDebitoTipo(),
							Util.somaBigDecimal(valor, valorPrestacao));
				}
				// Caso contrario inseri na coleÃ§Ã£o
				// primeiro registro do tipo.
				else {
					mapValoresPorTipoDebito.put(debitoACobrar.getDebitoTipo(), valorPrestacao);
				}
			} // fim do laÃ§o de debito a cobrar
		} // fim gerar debitos cobrados

		helper.setColecaoDebitoACobrar(colecaoDebitosACobrarUpdate);
		helper.setMapDebitosCobrados(mapDebitosCobrados);
		helper.setValorTotalDebito(valorTotalDebitos);
		helper.setMapValoresPorTipoDebito(mapValoresPorTipoDebito);

		return helper;
	}

		public GerarCreditoRealizadoHelper gerarCreditoRealizado(Imovel imovel, Integer anoMesFaturamentoGrupo,
			DeterminarValoresFaturamentoAguaEsgotoHelper helperValoresAguaEsgoto, BigDecimal valorTotalDebitos,
			boolean gerarAtividadeGrupoFaturamento, boolean preFaturamento) throws ControladorException {

		GerarCreditoRealizadoHelper helper = new GerarCreditoRealizadoHelper();

		Collection colecaoCreditosARealizar = obterTodosCreditosARealizarImovel(imovel, anoMesFaturamentoGrupo,
				preFaturamento);

		Collection colecaoCreditosARealizarUpdate = new ArrayList();

		Map<CreditoRealizado, Collection<CreditoRealizadoCategoria>> mapCreditoRealizado = null;
		Map<CreditoTipo, BigDecimal> mapValoresPorTipoCredito = null;

		BigDecimal valorTotalCreditos = BigDecimal.ZERO;

		if (colecaoCreditosARealizar != null && !colecaoCreditosARealizar.isEmpty()) {

			mapCreditoRealizado = new HashMap();
			mapValoresPorTipoCredito = new HashMap<CreditoTipo, BigDecimal>();

			AtualizacaoCreditoARealizarHelper atualizacaoHelper = new FaturamentoUtil().atualizarCreditosARealizar(
					anoMesFaturamentoGrupo, colecaoCreditosARealizar, helperValoresAguaEsgoto.getValorTotalAgua(),
					helperValoresAguaEsgoto.getValorTotalEsgoto(), valorTotalDebitos, preFaturamento);

			for (ItemCreditoARealizar itemCredito : atualizacaoHelper.getCreditos()) {
				CreditoARealizar creditoARealizar = itemCredito.getCreditoARelizar();

				if (gerarAtividadeGrupoFaturamento) {

					CreditoRealizado creditoRealizado = criarCreditoRealizado(itemCredito);
					Collection colecaoCreditosRealizadoCategoria = criarCreditoRealizadoCategoria(creditoRealizado,
							itemCredito);

					mapCreditoRealizado.put(creditoRealizado, colecaoCreditosRealizadoCategoria);

					colecaoCreditosARealizarUpdate.add(creditoARealizar);
				}

				if (mapValoresPorTipoCredito.containsKey(creditoARealizar.getCreditoTipo())) {
					BigDecimal valor = mapValoresPorTipoCredito.get(creditoARealizar.getCreditoTipo());
					mapValoresPorTipoCredito.put(creditoARealizar.getCreditoTipo(),
							Util.somaBigDecimal(valor, itemCredito.getCreditoCalculado()));
				} else {
					mapValoresPorTipoCredito.put(creditoARealizar.getCreditoTipo(), itemCredito.getCreditoCalculado());
				}
			}
			valorTotalCreditos = atualizacaoHelper.getValorTotalCreditos();
		}

		helper.setValorTotalCredito(valorTotalCreditos);
		helper.setColecaoCreditoARealizar(colecaoCreditosARealizarUpdate);
		helper.setMapCreditoRealizado(mapCreditoRealizado);
		helper.setMapValoresPorTipoCredito(mapValoresPorTipoCredito);

		return helper;
	}

	public GerarImpostosDeduzidosContaHelper gerarImpostosDeduzidosConta(Integer idImovel, Integer anoMesReferencia,
			BigDecimal valorAgua, BigDecimal valorEsgoto, BigDecimal valorDebito, BigDecimal valorCredito,
			boolean preFaturamento) throws ControladorException {

		GerarImpostosDeduzidosContaHelper retorno = new GerarImpostosDeduzidosContaHelper();

		try {
			Integer idCliente = repositorioFaturamento.pesquisarClienteResponsavelImovel(idImovel);

			if (idCliente != null) {
				ImpostoDeduzidoHelper helper = null;
				Collection<ImpostoDeduzidoHelper> colecaoHelper = null;

				BigDecimal baseCalculo = new BigDecimal("0.00");
				ImpostoTipoAliquota impostoTipoAliquota = null;
				BigDecimal valorImpostoDeduzido = new BigDecimal("0.00");
				BigDecimal percetagemAliquota = new BigDecimal("0.00");
				BigDecimal valorImpostoDeduzidoFinal = new BigDecimal("0.00");

				BigDecimal percetagemTotalAliquota = new BigDecimal("0.00");
				BigDecimal valorImpostoDeduzidoTotal = new BigDecimal("0.00");

				/*
				 * Determina a base de calculo dos impostos deduzido = (valor de Ã¡gua + valor
				 * de esgoto + valor dos dÃ©bitos cobrados - valor dos crÃ©ditos realizados).
				 */
				baseCalculo = valorAgua.add(valorEsgoto);
				baseCalculo = baseCalculo.add(valorDebito);
				baseCalculo = baseCalculo.subtract(valorCredito);

				baseCalculo = baseCalculo.setScale(2, BigDecimal.ROUND_DOWN);

				if (preFaturamento) {
					baseCalculo = ConstantesSistema.VALOR_ZERO;
				}

				EsferaPoder esferaPoder = getControladorImovel().obterEsferaPoderClienteResponsavel(idImovel);

				// Pesquisa a aliquota para cada tipo de imposto, por esfera de poder
				List<ImpostoTipoAliquota> aliquotas = repositorioFaturamento.pesquisarAliquotasImposto(esferaPoder.getId(), idImovel, anoMesReferencia);
				
				Iterator<ImpostoTipoAliquota> iteratorImpostoTipo = aliquotas.iterator();
				
				colecaoHelper = new ArrayList();
				
				while (iteratorImpostoTipo.hasNext()) {

					impostoTipoAliquota = iteratorImpostoTipo.next();
					
					helper = new ImpostoDeduzidoHelper();

					percetagemTotalAliquota = percetagemTotalAliquota
							.add(impostoTipoAliquota.getPercentualAliquota());

					/*
					 * O valor do Ãºltimo imposto nÃ£o serÃ¡ mais calculado, serÃ¡ a diferenÃ§a
					 * entre o valor total do imposto com o valor dos impostos calculados.
					 */
					if (iteratorImpostoTipo.hasNext()) {
						percetagemAliquota = Util.dividirArredondando(impostoTipoAliquota.getPercentualAliquota(),
								new BigDecimal("100.00"));
						valorImpostoDeduzido = baseCalculo.multiply(percetagemAliquota);
						valorImpostoDeduzido = valorImpostoDeduzido.setScale(2, BigDecimal.ROUND_HALF_DOWN);

						/*
						 * Se o valor deduzido for maior que zero, cria uma colecao com o tipo, o valor
						 * e a aliquota do imposto e guarda um valor total de todos os impostos.
						 */
						if (valorImpostoDeduzido.compareTo(ConstantesSistema.VALOR_ZERO) == 1 || preFaturamento) {
							helper.setIdImpostoTipo(impostoTipoAliquota.getImpostoTipoAliquota().getId());
							helper.setValor(valorImpostoDeduzido);
							helper.setPercentualAliquota(impostoTipoAliquota.getPercentualAliquota());
							valorImpostoDeduzidoFinal = valorImpostoDeduzidoFinal.add(valorImpostoDeduzido);

							colecaoHelper.add(helper);
						}
					} else {
						percetagemTotalAliquota = Util.dividirArredondando(percetagemTotalAliquota,
								new BigDecimal("100.00"));
						valorImpostoDeduzidoTotal = baseCalculo.multiply(percetagemTotalAliquota);
						valorImpostoDeduzidoTotal = valorImpostoDeduzidoTotal.setScale(2,
								BigDecimal.ROUND_HALF_DOWN);

						valorImpostoDeduzido = valorImpostoDeduzidoTotal.subtract(valorImpostoDeduzidoFinal);
						valorImpostoDeduzido = valorImpostoDeduzido.setScale(2, BigDecimal.ROUND_DOWN);

						/*
						 * Se o valor deduzido for maior que zero, cria uma colecao com o tipo, o valor
						 * e a aliquota do imposto e guarda um valor total de todos os impostos.
						 */
						if (valorImpostoDeduzido.compareTo(ConstantesSistema.VALOR_ZERO) == 1 || preFaturamento) {
							helper.setIdImpostoTipo(impostoTipoAliquota.getImpostoTipoAliquota().getId());
							helper.setValor(valorImpostoDeduzido);
							helper.setPercentualAliquota(impostoTipoAliquota.getPercentualAliquota());
							valorImpostoDeduzidoFinal = valorImpostoDeduzidoTotal;
							colecaoHelper.add(helper);
						}
					}
				}

				retorno.setListaImpostosDeduzidos(colecaoHelper);

				valorImpostoDeduzidoFinal = valorImpostoDeduzidoFinal.setScale(2, BigDecimal.ROUND_DOWN);

				retorno.setValorTotalImposto(valorImpostoDeduzidoFinal);
				retorno.setValorBaseCalculo(baseCalculo);

			} else {
				retorno.setListaImpostosDeduzidos(null);
				retorno.setValorTotalImposto(ConstantesSistema.VALOR_ZERO);
				retorno.setValorBaseCalculo(ConstantesSistema.VALOR_ZERO);
			}
		} catch (ErroRepositorioException ex) {
			sessionContext.setRollbackOnly();
			throw new ControladorException("erro.sistema", ex);
		}

		return retorno;
	}

	public Conta gerarConta(Imovel imovel, Integer anoMesFaturamento, SistemaParametro sistemaParametro,
			FaturamentoAtivCronRota faturamentoAtivCronRota,
			DeterminarValoresFaturamentoAguaEsgotoHelper helperValoresAguaEsgoto, GerarDebitoCobradoHelper helperDebito,
			GerarCreditoRealizadoHelper helperCredito,
			GerarImpostosDeduzidosContaHelper gerarImpostosDeduzidosContaHelper, FaturamentoGrupo faturamentoGrupo,
			boolean faturamentoAntecipado, boolean preFaturamento) throws ControladorException {

		// CONTA GERAL
		ContaGeral contaGeral = new ContaGeral();
		contaGeral.setIndicadorHistorico(ConstantesSistema.NAO);
		contaGeral.setUltimaAlteracao(new Date());

		// INSERINDO CONTA_GERAL NA BASE
		Integer idContaGeral = (Integer) getControladorUtil().inserir(contaGeral);
		contaGeral.setId(idContaGeral);

		// CONTA
		Conta conta = new Conta();
		conta.setImovel(imovel);
		conta.setReferencia(anoMesFaturamento);
		conta.setLigacaoAguaSituacao(imovel.getLigacaoAguaSituacao());
		conta.setLigacaoEsgotoSituacao(imovel.getLigacaoEsgotoSituacao());
		conta.setMotivoNaoEntregaDocumento(null);
		conta.setLocalidade(imovel.getLocalidade());
		conta.setQuadraConta(imovel.getQuadra());
		conta.setSubLote(imovel.getSubLote());
		conta.setLote(imovel.getLote());
		conta.setCodigoSetorComercial(imovel.getSetorComercial().getCodigo());
		conta.setQuadra(imovel.getQuadra().getNumeroQuadra());
		conta.setDigitoVerificadorConta(
				new Short(String.valueOf(Util.calculoRepresentacaoNumericaCodigoBarrasModulo10(anoMesFaturamento))));
		conta.setIndicadorCobrancaMulta((short) 2);

		if (faturamentoAntecipado) {
			conta.setDataVencimentoConta(this.determinarVencimentoContaAntecipado(imovel, anoMesFaturamento));
		} else {
			conta.setDataVencimentoConta(this.determinarVencimentoConta(imovel, faturamentoAtivCronRota,
					sistemaParametro, anoMesFaturamento));
		}

		conta.setDataVencimentoOriginal(conta.getDataVencimentoConta());

		if (helperValoresAguaEsgoto.getIndicadorFaturamentoAgua() != null
				&& helperValoresAguaEsgoto.getIndicadorFaturamentoAgua().equals(ConsumoHistorico.FATURAR_AGUA)) {
			conta.setConsumoAgua(helperValoresAguaEsgoto.getConsumoFaturadoAgua());
			conta.setConsumoRateioAgua(helperValoresAguaEsgoto.getConsumoRateioAgua());
		} else {
			conta.setConsumoAgua(0);
			conta.setConsumoRateioAgua(0);
		}

		if (helperValoresAguaEsgoto.getIndicadorFaturamentoEsgoto() != null
				&& helperValoresAguaEsgoto.getIndicadorFaturamentoEsgoto().equals(ConsumoHistorico.FATURAR_ESGOTO)) {
			conta.setConsumoEsgoto(helperValoresAguaEsgoto.getConsumoFaturadoEsgoto());
			conta.setConsumoRateioEsgoto(helperValoresAguaEsgoto.getConsumoRateioEsgoto());
		} else {
			conta.setConsumoEsgoto(0);
			conta.setConsumoRateioEsgoto(0);
		}

		try {
			if (!preFaturamento && imovel.isImovelMicroCondominio()) {
				BigDecimal[] valoresRateioAguaEsgotoImovel = this.calcularValorRateioImovel(imovel, faturamentoGrupo);

				BigDecimal valorRateioAgua = valoresRateioAguaEsgotoImovel[0];
				BigDecimal valorFinalAgua = helperValoresAguaEsgoto.getValorTotalAgua().add(valorRateioAgua);

				helperValoresAguaEsgoto.setValorTotalAgua(valorFinalAgua);
				conta.setValorRateioAgua(valorRateioAgua);

				if (imovel.getImovelCondominio().getLigacaoEsgotoSituacao().getIndicadorFaturamentoSituacao()
						.equals(LigacaoEsgotoSituacao.FATURAMENTO_ATIVO)) {
					BigDecimal valorRateioEsgoto = valoresRateioAguaEsgotoImovel[1];
					BigDecimal valorFinalEsgoto = helperValoresAguaEsgoto.getValorTotalEsgoto().add(valorRateioEsgoto);

					helperValoresAguaEsgoto.setValorTotalEsgoto(valorFinalEsgoto);
					conta.setValorRateioEsgoto(valorRateioEsgoto);
				}
			}
		} catch (ErroRepositorioException e) {
			e.printStackTrace();
		}

		Date dataValidadeConta = Util.adcionarOuSubtrairMesesAData(conta.getDataVencimentoConta(),
				sistemaParametro.getNumeroMesesValidadeConta(), 0);

		int mesDataValidadeConta = Util.getMes(dataValidadeConta);
		int anoDataValidadeConta = Util.getAno(dataValidadeConta);

		dataValidadeConta = Util.criarData(
				Integer.parseInt(Util.obterUltimoDiaMes(mesDataValidadeConta, anoDataValidadeConta)),
				mesDataValidadeConta, anoDataValidadeConta);

		conta.setDataValidadeConta(dataValidadeConta);
		conta.setReferenciaContabil(anoMesFaturamento);
		conta.setIndicadorAlteracaoVencimento((short) 2);

		conta.setValorAgua(helperValoresAguaEsgoto.getValorTotalAgua());
		conta.setValorEsgoto(helperValoresAguaEsgoto.getValorTotalEsgoto());
		conta.setValorCreditos(helperCredito.getValorTotalCredito());
		conta.setDebitos(helperDebito.getValorTotalDebito());

		if (gerarImpostosDeduzidosContaHelper.getValorTotalImposto() != null) {
			conta.setValorImposto(gerarImpostosDeduzidosContaHelper.getValorTotalImposto());
		} else {
			conta.setValorImposto(ConstantesSistema.VALOR_ZERO);
		}

		conta.setPercentualEsgoto(helperValoresAguaEsgoto.getPercentualEsgoto());
		conta.setPercentualColeta(helperValoresAguaEsgoto.getPercentualColetaEsgoto());
		conta.setDataInclusao(null);

		conta.setContaMotivoCancelamento(null);
		conta.setContaMotivoRetificacao(null);
		conta.setContaMotivoInclusao(null);
		conta.setFuncionarioEntrega(null);

		conta.setDataRetificacao(null);
		conta.setDataCancelamento(null);
		conta.setDataEmissao(new Date());
		conta.setReferenciaBaixaContabil(null);

		conta.setFaturamentoTipo(imovel.getFaturamentoTipo());
		conta.setConsumoTarifa(imovel.getConsumoTarifa());
		conta.setRegistroAtendimento(null);
		conta.setImovelPerfil(imovel.getImovelPerfil());
		conta.setIndicadorDebitoConta(imovel.getIndicadorDebitoConta());

		DebitoCreditoSituacao debitoCreditoSituacao = new DebitoCreditoSituacao();
		ContaMotivoRevisao contaMotivoRevisao = null;

		if (preFaturamento) {
			debitoCreditoSituacao.setId(DebitoCreditoSituacao.PRE_FATURADA);
		} else {
			debitoCreditoSituacao.setId(DebitoCreditoSituacao.NORMAL);
		}

		conta.setDebitoCreditoSituacaoAtual(debitoCreditoSituacao);

		if (contaMotivoRevisao != null) {
			conta.setDataRevisao(new Date());
		}

		conta.setUltimaAlteracao(new Date());
		conta.setContaGeral(contaGeral);
		conta.setId(contaGeral.getId());
		conta.setFaturamentoGrupo(faturamentoGrupo);
		conta.setRota(faturamentoAtivCronRota.getRota());

		Object[] leiturasAnteriorEAtual = getControladorMicromedicao()
				.obterLeituraAnteriorEAtualFaturamentoMedicaoHistorico(imovel.getId(), anoMesFaturamento);

		if (leiturasAnteriorEAtual != null) {
			Integer leituraAnteriorFaturamento = null;
			Integer leituraAtualFaturamento = null;

			if (leiturasAnteriorEAtual[0] != null) {
				leituraAnteriorFaturamento = (Integer) leiturasAnteriorEAtual[0];
			}
			if (leiturasAnteriorEAtual[1] != null) {
				leituraAtualFaturamento = (Integer) leiturasAnteriorEAtual[1];
			}

			conta.setNumeroLeituraAnterior(leituraAnteriorFaturamento);
			conta.setNumeroLeituraAtual(leituraAtualFaturamento);
		}

		conta.setNumeroBoleto(this.verificarGeracaoBoleto(sistemaParametro, conta));

		this.getControladorUtil().inserir(conta);

		return conta;
	}

	public GerarContaCategoriaHelper gerarContaCategoria(Conta conta, Collection colecaoCategoriaOUSubcategoria,
			Collection colecaoCalcularValoresAguaEsgotoHelper, SistemaParametro sistemaParametro)
			throws ControladorException {

		GerarContaCategoriaHelper helper = new GerarContaCategoriaHelper();

		logger.info("subcategoria 1");
		// Verificando se a empresa fatura por CATEGORIA ou SUBCATEGORIA
		if (sistemaParametro.getIndicadorTarifaCategoria().equals(SistemaParametro.INDICADOR_TARIFA_CATEGORIA)) {
			logger.info("subcategoria 2");
			// GERANDO POR CATEGORIA
			helper = this.gerarContaCategoriaPorCategoria(conta, colecaoCategoriaOUSubcategoria,
					colecaoCalcularValoresAguaEsgotoHelper);
		} else {
			logger.info("subcategoria 3");
			// GERANDO POR SUBCATEGORIA
			helper = this.gerarContaCategoriaPorSubcategoria(conta, colecaoCategoriaOUSubcategoria,
					colecaoCalcularValoresAguaEsgotoHelper);
		}

		return helper;
	}

	public void inserirClienteConta(Conta conta, Imovel imovel) throws ControladorException {

		Collection colecaoClienteImovel = null;

		/*
		 * Seleciona a partir da tabela CLIENTE_IMOVEL para IMOV_ID=Id do imÃ³vel e
		 * CLIM_DTRELACAOFIM com o valor correspondente a nulo.
		 */
		try {

			colecaoClienteImovel = this.repositorioFaturamento.pesquisarClienteImovelDataRelacaoFimNull(imovel);

		} catch (ErroRepositorioException ex) {
			sessionContext.setRollbackOnly();
			throw new ControladorException("erro.sistema", ex);
		}

		Iterator colecaoClienteImovelIt = colecaoClienteImovel.iterator();
		Object[] colecaoClienteImovelObjeto = null;
		ClienteConta clienteContaInsert = null;

		while (colecaoClienteImovelIt.hasNext()) {

			colecaoClienteImovelObjeto = (Object[]) colecaoClienteImovelIt.next();

			clienteContaInsert = new ClienteConta();
			clienteContaInsert.setConta(conta);
			clienteContaInsert.setCliente((Cliente) colecaoClienteImovelObjeto[0]);
			clienteContaInsert.setClienteRelacaoTipo((ClienteRelacaoTipo) colecaoClienteImovelObjeto[1]);
			clienteContaInsert.setIndicadorNomeConta((Short) colecaoClienteImovelObjeto[2]);
			clienteContaInsert.setUltimaAlteracao(new Date());

			this.getControladorUtil().inserir(clienteContaInsert);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void inserirContaImpostosDeduzidos(Conta conta,
			GerarImpostosDeduzidosContaHelper gerarImpostosDeduzidosContaHelper) throws ControladorException {
		// Incluir conta impostos deduzidos
		Collection colecaoImpostosDeduzidosHelper = gerarImpostosDeduzidosContaHelper.getListaImpostosDeduzidos();
		if (colecaoImpostosDeduzidosHelper != null && !colecaoImpostosDeduzidosHelper.isEmpty()) {

			Collection colecaoContaImpostosDeduzidosInserir = new ArrayList();

			Iterator iteratorColecaoImpostosDeduzidosHelper = colecaoImpostosDeduzidosHelper.iterator();

			ImpostoDeduzidoHelper impostoDeduzidoHelper = null;

			while (iteratorColecaoImpostosDeduzidosHelper.hasNext()) {
				impostoDeduzidoHelper = (ImpostoDeduzidoHelper) iteratorColecaoImpostosDeduzidosHelper.next();
				ContaImpostosDeduzidos contaImpostosDeduzidos = new ContaImpostosDeduzidos();
				contaImpostosDeduzidos.setConta(conta);
				ImpostoTipo impostoTipo = new ImpostoTipo();
				impostoTipo.setId(impostoDeduzidoHelper.getIdImpostoTipo());
				contaImpostosDeduzidos.setImpostoTipo(impostoTipo);
				contaImpostosDeduzidos.setValorImposto(impostoDeduzidoHelper.getValor());
				contaImpostosDeduzidos.setPercentualAliquota(impostoDeduzidoHelper.getPercentualAliquota());
				contaImpostosDeduzidos.setUltimaAlteracao(new Date());

				contaImpostosDeduzidos.setValorBaseCalculo(gerarImpostosDeduzidosContaHelper.getValorBaseCalculo());

				colecaoContaImpostosDeduzidosInserir.add(contaImpostosDeduzidos);
			}

			if (colecaoContaImpostosDeduzidosInserir != null && !colecaoContaImpostosDeduzidosInserir.isEmpty()) {
				this.getControladorBatch().inserirColecaoObjetoParaBatch(colecaoContaImpostosDeduzidosInserir);

				colecaoContaImpostosDeduzidosInserir.clear();
				colecaoContaImpostosDeduzidosInserir = null;
			}

		}

		if (colecaoImpostosDeduzidosHelper != null) {
			colecaoImpostosDeduzidosHelper.clear();
			colecaoImpostosDeduzidosHelper = null;
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void inserirDebitoCobrado(Map<DebitoCobrado, Collection<DebitoCobradoCategoria>> mapDebitosCobrados,
			Conta conta) throws ControladorException {

		if (mapDebitosCobrados != null && !mapDebitosCobrados.isEmpty()) {

			Collection colecaoDebitosCobradosCategoriaInserir = new ArrayList();

			// PARA INSERIR DEBITO_CONRADO_CATEGORIA
			Iterator iteratorColecaoDebitosCobrados = mapDebitosCobrados.keySet().iterator();

			DebitoCobrado debitoCobrado = null;

			Collection colecaoDebitoCobradoCategoriaDebitoCobrado = null;

			while (iteratorColecaoDebitosCobrados.hasNext()) {

				// DEBITO_COBRADO QUE SERÃ INSERIDO
				debitoCobrado = (DebitoCobrado) iteratorColecaoDebitosCobrados.next();

				// COLEÃÃO COM OS DEBITOS_COBRADOS POR CATEGORIA QUE SERÃO
				// INSERIDOS
				colecaoDebitoCobradoCategoriaDebitoCobrado = mapDebitosCobrados.get(debitoCobrado);

				debitoCobrado.setConta(conta);
				debitoCobrado.setDebitoCobrado(new Date());
				debitoCobrado.setUltimaAlteracao(new Date());

				Integer idDebitoCobrado = (Integer) this.getControladorUtil().inserir(debitoCobrado);

				Iterator iteratorColecaoDebitosCobradoCategoria = colecaoDebitoCobradoCategoriaDebitoCobrado.iterator();

				while (iteratorColecaoDebitosCobradoCategoria.hasNext()) {

					DebitoCobradoCategoria debitoCobradoCategoria = (DebitoCobradoCategoria) iteratorColecaoDebitosCobradoCategoria
							.next();

					debitoCobradoCategoria.getComp_id().setDebitoCobradoId(idDebitoCobrado);
					debitoCobradoCategoria.setUltimaAlteracao(new Date());

					colecaoDebitosCobradosCategoriaInserir.add(debitoCobradoCategoria);
				}
			}

			// INSERINDO DEBITO_COBRADO_CATEGORIA
			this.getControladorBatch().inserirColecaoObjetoParaBatch(colecaoDebitosCobradosCategoriaInserir);

			if (colecaoDebitosCobradosCategoriaInserir != null) {
				colecaoDebitosCobradosCategoriaInserir.clear();
				colecaoDebitosCobradosCategoriaInserir = null;
			}
		}
	}

	@SuppressWarnings("rawtypes")
	protected void atualizarDebitoACobrarFaturamento(Collection<DebitoACobrar> colecaoDebitosACobrarUpdate)
			throws ControladorException {
		if (colecaoDebitosACobrarUpdate != null && !colecaoDebitosACobrarUpdate.isEmpty()) {

			Iterator iteratorColecaoDebitosACobrarUpdate = colecaoDebitosACobrarUpdate.iterator();

			DebitoACobrar debitoACobrar = null;

			while (iteratorColecaoDebitosACobrarUpdate.hasNext()) {
				debitoACobrar = (DebitoACobrar) iteratorColecaoDebitosACobrarUpdate.next();
				try {

					repositorioFaturamento.atualizarDebitoAcobrar(debitoACobrar);

				} catch (ErroRepositorioException e) {
					sessionContext.setRollbackOnly();
					throw new ControladorException("erro.sistema", e);
				}
			}

			colecaoDebitosACobrarUpdate.clear();
			colecaoDebitosACobrarUpdate = null;

		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void inserirCreditoRealizado(
			Map<CreditoRealizado, Collection<CreditoRealizadoCategoria>> mapCreditoRealizado, Conta conta)
			throws ControladorException {
		// inserir credito realizado
		// inserir credito realizado categoria
		if (mapCreditoRealizado != null && !mapCreditoRealizado.isEmpty()) {

			Collection colecaoCreditoRealizadoCategoria = new ArrayList();

			Iterator iteratorColecaoCreditosRealizadosCreditosARealizar = mapCreditoRealizado.keySet().iterator();

			CreditoRealizado creditoRealizado = null;

			while (iteratorColecaoCreditosRealizadosCreditosARealizar.hasNext()) {

				creditoRealizado = (CreditoRealizado) iteratorColecaoCreditosRealizadosCreditosARealizar.next();

				Collection colecaoCreditosRealizadosCategoriaCreditoRealizado = mapCreditoRealizado
						.get(creditoRealizado);

				creditoRealizado.setConta(conta);
				creditoRealizado.setUltimaAlteracao(new Date());
				Integer idCreditoRealizado = (Integer) this.getControladorUtil().inserir(creditoRealizado);
				creditoRealizado.setId(idCreditoRealizado);

				Iterator iteratorColecaoCreditosRealizadosCategoriaCreditoRealizado = colecaoCreditosRealizadosCategoriaCreditoRealizado
						.iterator();

				CreditoRealizadoCategoria creditoRealizadoCategoria = null;

				while (iteratorColecaoCreditosRealizadosCategoriaCreditoRealizado.hasNext()) {
					creditoRealizadoCategoria = (CreditoRealizadoCategoria) iteratorColecaoCreditosRealizadosCategoriaCreditoRealizado
							.next();
					creditoRealizadoCategoria.setUltimaAlteracao(new Date());
					creditoRealizadoCategoria.getComp_id().setCreditoRealizado(creditoRealizado);
					colecaoCreditoRealizadoCategoria.add(creditoRealizadoCategoria);
				}
			}

			this.getControladorBatch().inserirColecaoObjetoParaBatch(colecaoCreditoRealizadoCategoria);

			if (colecaoCreditoRealizadoCategoria != null) {
				colecaoCreditoRealizadoCategoria.clear();
				colecaoCreditoRealizadoCategoria = null;
			}

			mapCreditoRealizado.clear();
			mapCreditoRealizado = null;
		}
	}

	@SuppressWarnings("rawtypes")
	protected void atualizarCreditoARealizar(Collection colecaoCreditosARealizarUpdate) throws ControladorException {
		// atualizar creditoARealizar
		if (colecaoCreditosARealizarUpdate != null && !colecaoCreditosARealizarUpdate.isEmpty()) {

			Iterator iteratorColecaoCreditosARealizarUpdate = colecaoCreditosARealizarUpdate.iterator();
			CreditoARealizar creditoARealizar = null;
			while (iteratorColecaoCreditosARealizarUpdate.hasNext()) {
				creditoARealizar = (CreditoARealizar) iteratorColecaoCreditosARealizarUpdate.next();
				try {

					repositorioFaturamento.atualizarCreditoARealizar(creditoARealizar);
				} catch (ErroRepositorioException e) {
					sessionContext.setRollbackOnly();
					throw new ControladorException("erro.sistema", e);
				}
			}

			colecaoCreditosARealizarUpdate.clear();
			colecaoCreditosARealizarUpdate = null;

		}
	}

	public ContaImpressao gerarContaImpressao(Conta conta, FaturamentoGrupo faturamentoGrupo, Imovel imovel, Rota rota)
			throws ControladorException {

		ContaImpressao contaImpressao = new ContaImpressao();

		contaImpressao.setContaGeral(conta.getContaGeral());
		contaImpressao.setId(conta.getContaGeral().getId());
		contaImpressao.setReferenciaConta(conta.getReferencia());
		contaImpressao.setFaturamentoGrupo(faturamentoGrupo);
		contaImpressao.setIndicadorImpressao(ConstantesSistema.NAO);
		contaImpressao.setUltimaAlteracao(new Date());

		Object idClienteResponsavel = null;

		if (conta.getContaMotivoRevisao() == null
				&& imovel.getImovelContaEnvio().getId().equals(ImovelContaEnvio.ENVIAR_CLIENTE_RESPONSAVEL)
				|| imovel.getImovelContaEnvio().getId()
						.equals(ImovelContaEnvio.NAO_PAGAVEL_IMOVEL_PAGAVEL_RESPONSAVEL)) {

			try {

				idClienteResponsavel = repositorioFaturamento.pesquisarClienteResponsavel(imovel.getId());

			} catch (ErroRepositorioException e) {
				sessionContext.setRollbackOnly();
				throw new ControladorException("erro.sistema", e);
			}

			if (idClienteResponsavel != null) {
				Cliente cliente = new Cliente();
				cliente.setId((Integer) idClienteResponsavel);

				contaImpressao.setClienteResponsavel(cliente);
			}

		}

		contaImpressao
				.setContaTipo(this.obterContaTipoParaContaImpressao(conta, (Integer) idClienteResponsavel, imovel));

		if (idClienteResponsavel == null) {

			Integer idEmpresaContaImpressao = null;

			try {

				idEmpresaContaImpressao = repositorioMicromedicao.obterIdEmpresaPorRota(rota);

			} catch (ErroRepositorioException e) {
				sessionContext.setRollbackOnly();
				throw new ControladorException("erro.sistema", e);
			}

			if (idEmpresaContaImpressao != null) {
				Empresa empresaContaImpressao = new Empresa();
				empresaContaImpressao.setId(idEmpresaContaImpressao);

				contaImpressao.setEmpresa(empresaContaImpressao);
			}
		}

		contaImpressao.setValorConta(conta.getValorTotalContaBigDecimal());

		// INSERINDO CONTA_IMPRESSAO NA BASE
		getControladorUtil().inserir(contaImpressao);

		return contaImpressao;
	}

	public GerarImpostosDeduzidosContaHelper gerarImpostosDeduzidosConta(Integer idImovel, Integer anoMesReferencia,
			BigDecimal valorAgua, BigDecimal valorEsgoto, BigDecimal valorDebito, BigDecimal valorCredito,
			boolean preFaturamento) throws ControladorException {

		GerarImpostosDeduzidosContaHelper retorno = new GerarImpostosDeduzidosContaHelper();

		try {
			Integer idCliente = repositorioFaturamento.pesquisarClienteResponsavelImovel(idImovel);

			if (idCliente != null) {
				ImpostoDeduzidoHelper helper = null;
				Collection<ImpostoDeduzidoHelper> colecaoHelper = null;

				BigDecimal baseCalculo = new BigDecimal("0.00");
				ImpostoTipoAliquota impostoTipoAliquota = null;
				BigDecimal valorImpostoDeduzido = new BigDecimal("0.00");
				BigDecimal percetagemAliquota = new BigDecimal("0.00");
				BigDecimal valorImpostoDeduzidoFinal = new BigDecimal("0.00");

				BigDecimal percetagemTotalAliquota = new BigDecimal("0.00");
				BigDecimal valorImpostoDeduzidoTotal = new BigDecimal("0.00");

				/*
				 * Determina a base de calculo dos impostos deduzido = (valor de Ã¡gua + valor
				 * de esgoto + valor dos dÃ©bitos cobrados - valor dos crÃ©ditos realizados).
				 */
				baseCalculo = valorAgua.add(valorEsgoto);
				baseCalculo = baseCalculo.add(valorDebito);
				baseCalculo = baseCalculo.subtract(valorCredito);

				baseCalculo = baseCalculo.setScale(2, BigDecimal.ROUND_DOWN);

				if (preFaturamento) {
					baseCalculo = ConstantesSistema.VALOR_ZERO;
				}

				EsferaPoder esferaPoder = getControladorImovel().obterEsferaPoderClienteResponsavel(idImovel);

				// Pesquisa a aliquota para cada tipo de imposto, por esfera de poder
				List<ImpostoTipoAliquota> aliquotas = repositorioFaturamento.pesquisarAliquotasImposto(esferaPoder.getId(), idImovel, anoMesReferencia);
				
				Iterator<ImpostoTipoAliquota> iteratorImpostoTipo = aliquotas.iterator();
				
				colecaoHelper = new ArrayList();
				
				while (iteratorImpostoTipo.hasNext()) {

					impostoTipoAliquota = iteratorImpostoTipo.next();
					
					helper = new ImpostoDeduzidoHelper();

					percetagemTotalAliquota = percetagemTotalAliquota
							.add(impostoTipoAliquota.getPercentualAliquota());

					/*
					 * O valor do Ãºltimo imposto nÃ£o serÃ¡ mais calculado, serÃ¡ a diferenÃ§a
					 * entre o valor total do imposto com o valor dos impostos calculados.
					 */
					if (iteratorImpostoTipo.hasNext()) {
						percetagemAliquota = Util.dividirArredondando(impostoTipoAliquota.getPercentualAliquota(),
								new BigDecimal("100.00"));
						valorImpostoDeduzido = baseCalculo.multiply(percetagemAliquota);
						valorImpostoDeduzido = valorImpostoDeduzido.setScale(2, BigDecimal.ROUND_HALF_DOWN);

						/*
						 * Se o valor deduzido for maior que zero, cria uma colecao com o tipo, o valor
						 * e a aliquota do imposto e guarda um valor total de todos os impostos.
						 */
						if (valorImpostoDeduzido.compareTo(ConstantesSistema.VALOR_ZERO) == 1 || preFaturamento) {
							helper.setIdImpostoTipo(impostoTipoAliquota.getImpostoTipoAliquota().getId());
							helper.setValor(valorImpostoDeduzido);
							helper.setPercentualAliquota(impostoTipoAliquota.getPercentualAliquota());
							valorImpostoDeduzidoFinal = valorImpostoDeduzidoFinal.add(valorImpostoDeduzido);

							colecaoHelper.add(helper);
						}
					} else {
						percetagemTotalAliquota = Util.dividirArredondando(percetagemTotalAliquota,
								new BigDecimal("100.00"));
						valorImpostoDeduzidoTotal = baseCalculo.multiply(percetagemTotalAliquota);
						valorImpostoDeduzidoTotal = valorImpostoDeduzidoTotal.setScale(2,
								BigDecimal.ROUND_HALF_DOWN);

						valorImpostoDeduzido = valorImpostoDeduzidoTotal.subtract(valorImpostoDeduzidoFinal);
						valorImpostoDeduzido = valorImpostoDeduzido.setScale(2, BigDecimal.ROUND_DOWN);

						/*
						 * Se o valor deduzido for maior que zero, cria uma colecao com o tipo, o valor
						 * e a aliquota do imposto e guarda um valor total de todos os impostos.
						 */
						if (valorImpostoDeduzido.compareTo(ConstantesSistema.VALOR_ZERO) == 1 || preFaturamento) {
							helper.setIdImpostoTipo(impostoTipoAliquota.getImpostoTipoAliquota().getId());
							helper.setValor(valorImpostoDeduzido);
							helper.setPercentualAliquota(impostoTipoAliquota.getPercentualAliquota());
							valorImpostoDeduzidoFinal = valorImpostoDeduzidoTotal;
							colecaoHelper.add(helper);
						}
					}
				}

				retorno.setListaImpostosDeduzidos(colecaoHelper);

				valorImpostoDeduzidoFinal = valorImpostoDeduzidoFinal.setScale(2, BigDecimal.ROUND_DOWN);

				retorno.setValorTotalImposto(valorImpostoDeduzidoFinal);
				retorno.setValorBaseCalculo(baseCalculo);

			} else {
				retorno.setListaImpostosDeduzidos(null);
				retorno.setValorTotalImposto(ConstantesSistema.VALOR_ZERO);
				retorno.setValorBaseCalculo(ConstantesSistema.VALOR_ZERO);
			}
		} catch (ErroRepositorioException ex) {
			sessionContext.setRollbackOnly();
			throw new ControladorException("erro.sistema", ex);
		}

		return retorno;
	}

	public void gerarResumoFaturamentoSimulacao(Collection colecaoCategorias,
			Collection colecaoCalcularValoresAguaEsgotoHelper, GerarDebitoCobradoHelper helperDebito,
			GerarCreditoRealizadoHelper helperCredito, Collection colecaoResumoFaturamento, Imovel imovel,
			boolean gerarAtividadeGrupoFaturamento, FaturamentoAtivCronRota faturamentoAtivCronRota,
			FaturamentoGrupo faturamentoGrupo, Integer anoMesReferenciaResumoFaturamento, boolean preFaturar)
			throws ControladorException {

		if (anoMesReferenciaResumoFaturamento == null) {
			anoMesReferenciaResumoFaturamento = faturamentoGrupo.getAnoMesReferencia();
		}

		if ((colecaoCategorias != null && !colecaoCategorias.isEmpty())
				&& (colecaoCalcularValoresAguaEsgotoHelper != null
						&& !colecaoCalcularValoresAguaEsgotoHelper.isEmpty())) {

			/*
			 * Colocado por Raphael Rossiter em 26/03/2007
			 * 
			 * OBJ: Gerar o resumo da simulaÃ§Ã£o do faturamento com os valores por
			 * categoria e nÃ£o com o valor total.
			 */

			// ColeÃ§Ã£o com os valores por Categoria (DÃBITOS)
			Collection colecaoValoresDebitosCategorias = getControladorImovel()
					.obterValorPorCategoria(colecaoCategorias, helperDebito.getValorTotalDebito());

			Map<DebitoTipo, Collection<BigDecimal>> valoresDebitosCategoriasPorTipoDebito = new HashMap<DebitoTipo, Collection<BigDecimal>>();

			if (helperDebito.getMapValoresPorTipoDebito() != null) {
				for (Map.Entry debitos : helperDebito.getMapValoresPorTipoDebito().entrySet()) {
					Collection colecaoValoresDebitosCategoriasPorTipoDebito = getControladorImovel()
							.obterValorPorCategoria(colecaoCategorias, (BigDecimal) debitos.getValue());

					valoresDebitosCategoriasPorTipoDebito.put((DebitoTipo) debitos.getKey(),
							colecaoValoresDebitosCategoriasPorTipoDebito);

				}
			}

			// ColeÃ§Ã£o com os valores por Categoria (CRÃDITOS)
			Collection colecaoValoresCreditosCategorias = getControladorImovel()
					.obterValorPorCategoria(colecaoCategorias, helperCredito.getValorTotalCredito());

			Map<CreditoTipo, Collection<BigDecimal>> valoresDebitosCategoriasPorTipoCredito = new HashMap<CreditoTipo, Collection<BigDecimal>>();

			if (helperCredito.getMapValoresPorTipoCredito() != null) {
				for (Map.Entry creditos : helperCredito.getMapValoresPorTipoCredito().entrySet()) {
					Collection colecaoValoresCreditosCategoriasPorTipoCredito = getControladorImovel()
							.obterValorPorCategoria(colecaoCategorias, (BigDecimal) creditos.getValue());

					valoresDebitosCategoriasPorTipoCredito.put((CreditoTipo) creditos.getKey(),
							colecaoValoresCreditosCategoriasPorTipoCredito);

				}
			}

			// ColeÃ§Ã£o de categorias
			Iterator iteratorColecaoCategorias = colecaoCategorias.iterator();

			// colecao com os valores para ser usados em conta categoria
			Iterator iteratorColecaoCalcularValoresAguaEsgotoHelper = colecaoCalcularValoresAguaEsgotoHelper.iterator();

			// ColeÃ§Ã£o com os valores por Categoria (DÃBITOS)
			Iterator iteratorColecaoValoresDebitosCategorias = colecaoValoresDebitosCategorias.iterator();

			// ColeÃ§Ã£o com os valores por Categoria (CRÃDITOS)
			Iterator iteratorColecaoValoresCreditosCategorias = colecaoValoresCreditosCategorias.iterator();

			Categoria categoria = null;
			CalcularValoresAguaEsgotoHelper calcularValoresAguaEsgotoHelper = null;
			BigDecimal valorTotalDebitoCategoria = null;
			BigDecimal valorTotalCreditoCategoria = null;

			boolean primeiraCategoria = true;
			int contador = 0;
			while (iteratorColecaoCategorias.hasNext() && iteratorColecaoCalcularValoresAguaEsgotoHelper.hasNext()
					&& iteratorColecaoValoresDebitosCategorias.hasNext()
					&& iteratorColecaoValoresCreditosCategorias.hasNext()) {

				categoria = (Categoria) iteratorColecaoCategorias.next();

				calcularValoresAguaEsgotoHelper = (CalcularValoresAguaEsgotoHelper) iteratorColecaoCalcularValoresAguaEsgotoHelper
						.next();

				valorTotalDebitoCategoria = (BigDecimal) iteratorColecaoValoresDebitosCategorias.next();

				valorTotalCreditoCategoria = (BigDecimal) iteratorColecaoValoresCreditosCategorias.next();

				Collection colecaoValoresPorTipoDebito = new ArrayList();
				for (Map.Entry debitos : valoresDebitosCategoriasPorTipoDebito.entrySet()) {
					ValorPorTipoRegistroHelper helper = new ValorPorTipoRegistroHelper();
					helper.setDebitoTipo((DebitoTipo) debitos.getKey());
					Collection colecaoValoresPorCategoria = (Collection) debitos.getValue();
					helper.setValor((BigDecimal) colecaoValoresPorCategoria.toArray()[contador]);

					colecaoValoresPorTipoDebito.add(helper);
				}

				Collection colecaoValoresPorTipoCredito = new ArrayList();
				for (Map.Entry creditos : valoresDebitosCategoriasPorTipoCredito.entrySet()) {
					ValorPorTipoRegistroHelper helper = new ValorPorTipoRegistroHelper();
					helper.setCreditoTipo((CreditoTipo) creditos.getKey());
					Collection colecaoValoresPorCategoria = (Collection) creditos.getValue();
					helper.setValor((BigDecimal) colecaoValoresPorCategoria.toArray()[contador]);

					colecaoValoresPorTipoCredito.add(helper);
				}

				BigDecimal valorImpostos = BigDecimal.ZERO;

				if (helperDebito.getGerarImpostosDeduzidosContaHelper() != null
						&& helperDebito.getGerarImpostosDeduzidosContaHelper().getValorTotalImposto() != null) {
					valorImpostos = helperDebito.getGerarImpostosDeduzidosContaHelper().getValorTotalImposto();
				}

				// [SB0009] - Gerar Resumo da SimulaÃ§Ã£o do Faturamento
				this.adicionarColecaoResumoFaturamentoSimulacao(colecaoResumoFaturamento, categoria,
						Subcategoria.SUBCATEGORIA_ZERO, calcularValoresAguaEsgotoHelper, imovel,
						gerarAtividadeGrupoFaturamento, valorTotalDebitoCategoria, valorTotalCreditoCategoria,
						faturamentoAtivCronRota, anoMesReferenciaResumoFaturamento, primeiraCategoria, preFaturar,
						colecaoValoresPorTipoDebito, colecaoValoresPorTipoCredito, valorImpostos);

				contador++;
				primeiraCategoria = false;
			}
		} else if (colecaoCategorias != null && !colecaoCategorias.isEmpty()) {

			/*
			 * Colocado por Raphael Rossiter em 26/03/2007
			 * 
			 * OBJ: Gerar o resumo da simulaÃ§Ã£o do faturamento com os valores por
			 * categoria e nÃ£o com o valor total
			 */

			// ColeÃ§Ã£o com os valores por Categoria (DÃBITOS)
			Collection colecaoValoresDebitosCategorias = getControladorImovel()
					.obterValorPorCategoria(colecaoCategorias, helperDebito.getValorTotalDebito());

			// ColeÃ§Ã£o com os valores por Categoria (CRÃDITOS)
			Collection colecaoValoresCreditosCategorias = getControladorImovel()
					.obterValorPorCategoria(colecaoCategorias, helperCredito.getValorTotalCredito());

			// ColeÃ§Ã£o de categorias
			Iterator iteratorColecaoCategorias = colecaoCategorias.iterator();

			// ColeÃ§Ã£o com os valores por Categoria (DÃBITOS)
			Iterator iteratorColecaoValoresDebitosCategorias = colecaoValoresDebitosCategorias.iterator();

			Map<DebitoTipo, Collection<BigDecimal>> valoresDebitosCategoriasPorTipoDebito = new HashMap<DebitoTipo, Collection<BigDecimal>>();

			if (helperDebito.getMapValoresPorTipoDebito() != null) {

				for (Map.Entry debitos : helperDebito.getMapValoresPorTipoDebito().entrySet()) {
					Collection colecaoValoresDebitosCategoriasPorTipoDebito = getControladorImovel()
							.obterValorPorCategoria(colecaoCategorias, (BigDecimal) debitos.getValue());

					valoresDebitosCategoriasPorTipoDebito.put((DebitoTipo) debitos.getKey(),
							colecaoValoresDebitosCategoriasPorTipoDebito);

				}

			}

			// ColeÃ§Ã£o com os valores por Categoria (CRÃDITOS)
			Iterator iteratorColecaoValoresCreditosCategorias = colecaoValoresCreditosCategorias.iterator();

			Map<CreditoTipo, Collection<BigDecimal>> valoresDebitosCategoriasPorTipoCredito = new HashMap<CreditoTipo, Collection<BigDecimal>>();

			if (helperCredito.getMapValoresPorTipoCredito() != null) {

				for (Map.Entry creditos : helperCredito.getMapValoresPorTipoCredito().entrySet()) {
					Collection colecaoValoresCreditosCategoriasPorTipoCredito = getControladorImovel()
							.obterValorPorCategoria(colecaoCategorias, (BigDecimal) creditos.getValue());

					valoresDebitosCategoriasPorTipoCredito.put((CreditoTipo) creditos.getKey(),
							colecaoValoresCreditosCategoriasPorTipoCredito);

				}

			}

			Categoria categoria = null;
			BigDecimal valorTotalDebitoCategoria = null;
			BigDecimal valorTotalCreditoCategoria = null;
			int contador = 0;
			boolean primeiraCategoria = true;
			while (iteratorColecaoCategorias.hasNext() && iteratorColecaoValoresDebitosCategorias.hasNext()
					&& iteratorColecaoValoresCreditosCategorias.hasNext()) {

				categoria = (Categoria) iteratorColecaoCategorias.next();

				valorTotalDebitoCategoria = (BigDecimal) iteratorColecaoValoresDebitosCategorias.next();

				valorTotalCreditoCategoria = (BigDecimal) iteratorColecaoValoresCreditosCategorias.next();

				Collection colecaoValoresPorTipoDebito = new ArrayList();
				for (Map.Entry debitos : valoresDebitosCategoriasPorTipoDebito.entrySet()) {
					ValorPorTipoRegistroHelper helper = new ValorPorTipoRegistroHelper();
					helper.setDebitoTipo((DebitoTipo) debitos.getKey());
					Collection colecaoValoresPorCategoria = (Collection) debitos.getValue();
					helper.setValor((BigDecimal) colecaoValoresPorCategoria.toArray()[contador]);

					colecaoValoresPorTipoDebito.add(helper);
				}

				Collection colecaoValoresPorTipoCredito = new ArrayList();
				for (Map.Entry creditos : valoresDebitosCategoriasPorTipoCredito.entrySet()) {
					ValorPorTipoRegistroHelper helper = new ValorPorTipoRegistroHelper();
					helper.setCreditoTipo((CreditoTipo) creditos.getKey());
					Collection colecaoValoresPorCategoria = (Collection) creditos.getValue();
					helper.setValor((BigDecimal) colecaoValoresPorCategoria.toArray()[contador]);

					colecaoValoresPorTipoCredito.add(helper);
				}

				BigDecimal valorImpostos = BigDecimal.ZERO;

				if (helperDebito.getGerarImpostosDeduzidosContaHelper() != null
						&& helperDebito.getGerarImpostosDeduzidosContaHelper().getValorTotalImposto() != null) {
					valorImpostos = helperDebito.getGerarImpostosDeduzidosContaHelper().getValorTotalImposto();
				}

				// [SB0009] - Gerar Resumo da SimulaÃ§Ã£o do Faturamento
				this.adicionarColecaoResumoFaturamentoSimulacao(colecaoResumoFaturamento, categoria,
						Subcategoria.SUBCATEGORIA_ZERO, null, imovel, gerarAtividadeGrupoFaturamento,
						valorTotalDebitoCategoria, valorTotalCreditoCategoria, faturamentoAtivCronRota,
						anoMesReferenciaResumoFaturamento, primeiraCategoria, preFaturar, colecaoValoresPorTipoDebito,
						colecaoValoresPorTipoCredito, valorImpostos);

				contador++;
				primeiraCategoria = false;
			}
		}
	}
