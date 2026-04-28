function add() {
    // Dados EXATOS do descritivo de apoio
    const novoJogador = {
        nome: "Lucas Tolentino Coelho de Lima",
        dataNascimento: "27/08/1997 (28 anos)",
        altura: "1,80 m",
        posicao: "Meio-campista",
        rank: "8,8",
        imagem: "img/Lucas_Paqueta.webp",  // ✅ .webp
        imagemFallback: "img/Lucas_Paqueta.png"  // Fallback caso não carregue
    };

    // 1. Encontra o card original
    const cardOriginal = document.querySelector('.card, .player-card, [class*="card"]');
    if (!cardOriginal) {
        alert('❌ Card original não encontrado!');
        return;
    }
    
    // 2. CLONA card original (estrutura + Bootstrap 100% preservado)
    const novoCard = cardOriginal.cloneNode(true);
    
    // 3. ATUALIZA IMAGEM com suporte WEBP + FALLBACK
    const imgElement = novoCard.querySelector('img');
    if (imgElement) {
        imgElement.src = novoJogador.imagem;  // Tenta .webp primeiro
        imgElement.alt = novoJogador.nome;
        imgElement.onerror = function() {
            // Se .webp falhar, tenta .png
            this.src = novoJogador.imagemFallback;
        };
        imgElement.onload = function() {
            console.log('✅ Imagem carregada:', this.src);
        };
    }
    
    // 4. ATUALIZA TODOS os textos (IDs + fallback genérico)
    atualizarTexto(novoCard, '#nomeJogador, #nome, h3, .card-title, .nome', novoJogador.nome);
    atualizarTexto(novoCard, '#dataNascimento, #nascimento, #data, .nascimento', `📅 ${novoJogador.dataNascimento}`);
    atualizarTexto(novoCard, '#altura, .altura', `📏 ${novoJogador.altura}`);
    atualizarTexto(novoCard, '#posicao, .posicao', `🏃 ${novoJogador.posicao}`);
    atualizarTextoRank(novoCard, '#rank, .rank', novoJogador.rank);
    
    // 5. Remove IDs do clone (evita conflitos)
    novoCard.querySelectorAll('[id]').forEach(el => el.removeAttribute('id'));
    
    // 6. Insere AO LADO do original
    const botao = document.querySelector('button[onclick="add()"], .btn-add, button');
    if (botao && botao.parentNode) {
        botao.parentNode.insertBefore(novoCard, botao);
    } else {
        cardOriginal.parentNode.appendChild(novoCard);
    }
    
    // 7. ANIMAÇÃO PROFISSIONAL
    novoCard.style.opacity = '0';
    novoCard.style.transform = 'scale(0.9) translateY(30px)';
    novoCard.style.transition = 'all 0.6s cubic-bezier(0.34, 1.56, 0.64, 1)';
    
    setTimeout(() => {
        novoCard.style.opacity = '1';
        novoCard.style.transform = 'scale(1) translateY(0)';
    }, 100);
    
    // 8. Feedback botão
    feedbackBotao(botao);
    
    console.log('🎉 Novo jogador:', novoJogador.nome, '- Imagem:', novoJogador.imagem);
}

// Função auxiliar para atualizar texto
function atualizarTexto(card, seletor, texto) {
    const elemento = card.querySelector(seletor);
    if (elemento) elemento.textContent = texto;
}

// Função específica para rank (pode ter span interno)
function atualizarTextoRank(card, seletor, rank) {
    const elemento = card.querySelector(seletor);
    if (elemento) {
        elemento.innerHTML = `⭐ Rank: <strong>${rank}</strong>`;
        // Se tem span filho
        const span = elemento.querySelector('span');
        if (span) span.textContent = rank;
    }
}

// Feedback visual botão
function feedbackBotao(botao) {
    if (!botao) return;
    
    const original = {
        text: botao.textContent,
        bg: botao.style.backgroundColor,
        scale: botao.style.transform
    };
    
    botao.textContent = '✅ ADICIONADO!';
    botao.style.backgroundColor = '#28a745';
    botao.style.color = 'white';
    botao.style.transform = 'scale(1.05)';
    
    setTimeout(() => {
        botao.textContent = original.text;
        botao.style.backgroundColor = original.bg;
        botao.style.color = '';
        botao.style.transform = original.scale;
    }, 2000);
}
