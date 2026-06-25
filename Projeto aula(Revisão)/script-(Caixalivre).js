 const produtos = {
    "123": {"nome": "jose e Mateus", "preco": 9.000},
    "456": {"nome": "fala Mansa", "preco": 3.000},
    "789": {"nome": "Sabrina Carpinther", "preco": 12.000},
    "147": {"nome": "Gaucho da Fronteiro", "preco": 90.500},

 };

 let carrinho = [];

 const audio = new Audio("bip.mp3")

 window.onload = () => {
      document.getElementById("cod").focus();
 }

 function addProduto () {
    const codhtml = document.getElementById("cod")
    const qtdHtml = document.getElementById("qtd")

    const ValorCod = codHtml.valeu;
    const ValorQtd = qtdHtml.valeu;

    if(!produto [valorCod]) {
        AlertItem();
        return;
    }

    const infoProduto = produto[valorCod]

     const item = {
        nome: infoProduto.nome,
        preco: inforProduto.preco,
        quantidade: ValorQtd,
        subtot: infoProduto.preco * ValorQtd
     };

     carrinho.push(item);
     audio.currentTime = 0;
     audio.play();

     atulizartela();

     qtdHtml.value = 1;
     codHtml.value = "";



 }

